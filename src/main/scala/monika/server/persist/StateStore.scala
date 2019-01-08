package monika.server.persist

import java.io._
import java.nio.channels.Channels
import java.time.{LocalDate, LocalDateTime}

import monika.server.Constants.Locations
import monika.server.pure.Model._
import org.json4s.{DefaultFormats, Formats, JValue}
import org.json4s.native.{JsonMethods, Printer}
import org.slf4j.{Logger, LoggerFactory}
import JsonMethods._
import monika.Primitives._
import monika.server.proxy.ProxyServer.ProxySettings
import org.json4s.JsonAST.JNull
import org.json4s.JsonDSL._
import scalaz.Tag
import scalaz.syntax.id._

import scala.util.{Failure, Success, Try}

/**
  * - persists a single MonikaState to disk
  * - provides a method to perform a stateful transaction on MonikaState
  * - reports any errors to log
  */
object StateStore extends StateStoreH {

  private implicit val formats = DefaultFormats
  private val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  /**
    * - the caller can provide a function which modifies the state and returns some value R
    * - this is atomic, if an error occurs then no changes will be persisted
    * - no two transactions can occur at once, one must wait for the other
    */
  def transaction[R](fn: MonikaState => (MonikaState, R)): R = {
    this.synchronized {
      ensureFileWritable()
      val stateDBFile = new RandomAccessFile(Locations.StateJsonFile, "rw")
      val channel = stateDBFile.getChannel
      val lock = channel.tryLock()
      if (lock == null) {
        val message = s"a lock cannot be acquired for ${Locations.StateJsonFile}"
        LOGGER.error(message)
        throw new RuntimeException(message)
      }

      val input = Channels.newInputStream(channel)
      val state = readStateFromInput(input)
      val (newState, returnValue) = fn(state)

      val output = Channels.newOutputStream(channel)
      writeStateToOutput(newState, output)
      lock.release(); returnValue
    }
  }

  private def ensureFileWritable(): Unit = {
    val file = new File(Locations.StateJsonFile)
    val lastModified = file.lastModified()
    if (file.exists() && !file.canWrite) {
      val message = s"file is not writable ${Locations.StateJsonFile}"
      LOGGER.error(message)
      throw new RuntimeException(message)
    }

    val parent = file.getParentFile
    if (!file.exists() && !parent.exists()) {
      val message = s"parent folder does not exist ${parent.getCanonicalPath}"
      LOGGER.error(message)
      throw new RuntimeException(message)
    }

    if (!file.exists() && !parent.canWrite) {
      val message = s"file cannot be created in parent folder ${parent.getCanonicalPath}"
      LOGGER.error(message)
      throw new RuntimeException(message)
    }

    if (file.lastModified() != lastModified) {
      val message = s"lastModified changed since checks were performed ${parent.getCanonicalPath}"
      LOGGER.error(message)
      throw new RuntimeException(message)
    }
  }

  private def readStateFromInput(input: InputStream): MonikaState = {
    val json = Try(JsonMethods.parse(input)).orElseX(ex =>{
      val message = s"file does not contain a valid json format ${Locations.StateJsonFile}"
      LOGGER.error(message, ex)
      throw new RuntimeException(message, ex)
    })
    Try(jsonToState(json)).orElseX(ex => {
      val message = s"failed to deserialize JSON ${Locations.StateJsonFile}"
      LOGGER.error(message, ex)
      throw new RuntimeException(message, ex)
    })
  }

  private def writeStateToOutput(state: MonikaState, output: OutputStream): Unit = {
    val json: JValue = stateToJson(state)
    val writer = new OutputStreamWriter(output)
    Printer.compact(render(json), writer)
  }

  private[persist] def jsonToState(json: JValue): MonikaState = {
    def jsonToProxy(json: JValue): ProxySettings = {
      ProxySettings(
        transparent = (json \ "transparent").extract[Boolean],
        allowHtmlPrefix = (json \ "allow").extract[Vector[String]],
        rejectHtmlKeywords = (json \ "block").extract[Vector[String]]
      )
    }
    def jsonToProfile(json: JValue): Profile = {
      Profile(
        name = (json \ "name").extract[String],
        programs = (json \ "programs").extract[Vector[String]].map(FileName),
        projects = (json \ "projects").extract[Vector[String]].map(FileName),
        bookmarks = (json \ "bookmarks").extract[Vector[JValue]].map(v => {
          Bookmark((v \ "name").extract[String], (v \ "url").extract[String])
        }),
        proxy = jsonToProxy(json \ "proxy")
      )
    }
    def jsonToRequest(json: JValue): ProfileRequest = {
      ProfileRequest(
        start = LocalDateTime.parse((json \ "start").extract[String]),
        end = LocalDateTime.parse((json \ "end").extract[String]),
        profile = jsonToProfile(json \ "profile")
      )
    }
    MonikaState(
      active = (json \ "active").extract[JValue] |> (v => if (v == JNull) None else Some(jsonToRequest(v))),
      queue = (json \ "queue").extract[Vector[JValue]].map(jsonToRequest),
      knownProfiles = (json \ "profiles").extract[Vector[JValue]].map(jsonToProfile).map(p => p.name -> p).toMap,
      passwords = (json \ "passwords").extract[Vector[JValue]].map(v => (
        LocalDate.parse((v \ "date").extract[String]), (v \ "password").extract[String]
      )).toMap
    )
  }

  def main(args: Array[String]): Unit = {
    val jsonStr =
      """{
        |  "queue":[],
        |  "profiles":[],
        |  "passwords":[]
        |}
      """.stripMargin
    val json = JsonMethods.parse(jsonStr)
    val queue = (json \ "queue").extract[Vector[JValue]]
    println(queue)
  }

  private[persist] def stateToJson(state: MonikaState): JValue = {
    def proxyToJson(settings: ProxySettings): JValue = {
      ("transparent" -> settings.transparent) ~
      ("allow" -> settings.allowHtmlPrefix) ~
      ("block" -> settings.rejectHtmlKeywords)
    }
    def profileToJson(profile: Profile): JValue = {
      ("name" -> profile.name) ~
      ("programs" -> profile.programs.map(Tag.unwrap)) ~
      ("projects" -> profile.projects.map(Tag.unwrap)) ~
      ("bookmarks" -> profile.bookmarks.map(b => ("name" -> b.name) ~ ("url" -> b.url))) ~
      ("proxy" -> proxyToJson(profile.proxy))
    }
    def requestToJson(request: ProfileRequest): JValue = {
      ("start" -> request.start.toString) ~
      ("end" -> request.end.toString) ~
      ("profile" -> profileToJson(request.profile))
    }
    ("queue" -> state.queue.map(requestToJson)) ~
    ("active" -> state.active.map(requestToJson)) ~
    ("profiles" -> state.knownProfiles.values.map(profileToJson)) ~
    ("passwords" -> state.passwords.map(pair => {
      val (date, password) = pair
      ("date" -> date.toString) ~ ("password" -> password)
    }))
  }

}
