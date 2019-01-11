package monika.server

import java.io.{ByteArrayOutputStream, File, OutputStream, OutputStreamWriter}
import java.time.{LocalDate, LocalDateTime}

import monika.Primitives.FileName
import monika.server.Constants.Locations
import monika.server.LittleProxy.ProxySettings
import monika.server.Structs._
import org.apache.commons.io.FileUtils
import org.json4s.native.JsonMethods.render
import org.json4s.native.{JsonMethods, Printer}
import org.json4s.{DefaultFormats, JValue, JsonInput}
import org.slf4j.{Logger, LoggerFactory}
import scalaz.Tag
import monika.Primitives._
import org.json4s.JsonDSL._
import org.json4s.JsonAST._
import scalaz.syntax.id._

import scala.util.Try

/**
  * - persists a single MonikaState to disk
  * - provides a method to perform a stateful transaction on MonikaState
  * - reports any errors to log
  */
object Persistence {

  private implicit val formats = DefaultFormats
  private val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  /**
    * - the caller can provide a function which modifies the state and returns some value R
    * - this is atomic, if an error occurs then no changes will be persisted
    * - no two transactions can occur at once, one must wait for the other
    */
  def transaction[R](fn: MonikaState => (MonikaState, R)): R = {
    this.synchronized {
      val stateDBFile = new File(Locations.StateJsonFile)
      ensureFileWritable(stateDBFile)
      val state: MonikaState = {
        if (stateDBFile.exists()) {
          val input = FileUtils.readFileToString(stateDBFile, "UTF-8")
          readStateFromInput(input)
        } else InitialState
      }
      val (newState, returnValue) = fn(state)

      val output = new ByteArrayOutputStream()
      writeStateToOutput(newState, output)
      FileUtils.writeByteArrayToFile(stateDBFile, output.toByteArray); returnValue
    }
  }

  private def ensureFileWritable(file: File): Unit = {
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

  private def readStateFromInput(input: JsonInput): MonikaState = {
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

  private[server] def jsonToState(json: JValue): MonikaState = {
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
    def jsonToRequest(json: JValue): ActivateProfile = {
      ActivateProfile(
        start = LocalDateTime.parse((json \ "start").extract[String]),
        profile = jsonToProfile(json \ "profile")
      )
    }
    MonikaState(
      queue = (json \ "queue").extract[Vector[JValue]].map(jsonToRequest),
      knownProfiles = (json \ "profiles").extract[Vector[JValue]].map(jsonToProfile).map(p => p.name -> p).toMap,
      passwords = (json \ "passwords").extract[Vector[JValue]].map(v => (
        LocalDate.parse((v \ "date").extract[String]), (v \ "password").extract[String]
      )).toMap
    )
  }

  private[server] def stateToJson(state: MonikaState): JValue = {
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
    def requestToJson(request: ActivateProfile): JValue = {
      ("start" -> request.start.toString) ~
      ("profile" -> profileToJson(request.profile))
    }
    ("queue" -> state.queue.map(requestToJson)) ~
    ("profiles" -> state.knownProfiles.values.map(profileToJson)) ~
    ("passwords" -> state.passwords.map(pair => {
      val (date, password) = pair
      ("date" -> date.toString) ~ ("password" -> password)
    }))
  }

}
