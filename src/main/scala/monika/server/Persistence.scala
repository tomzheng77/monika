package monika.server

import java.io.{ByteArrayOutputStream, File, OutputStream, OutputStreamWriter}
import java.time.LocalDateTime

import monika.Primitives.{FileName, _}
import monika.server.Constants.Locations
import monika.server.LittleProxy.ProxySettings
import monika.server.Structs._
import org.apache.commons.io.FileUtils
import scalaz.Tag

import scala.util.Try

/**
  * - persists a single MonikaState to disk
  * - provides a method to perform a stateful transaction on MonikaState
  * - reports any errors to log
  */
object Persistence extends UseLogger with UseJSON {

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
        } else MonikaState()
      }
      val (newState, returnValue) = fn(state)

      val output = new ByteArrayOutputStream()
      writeStateToOutput(newState, output)
      FileUtils.writeByteArrayToFile(stateDBFile, output.toByteArray); returnValue
    }
  }

  def readStateOrDefault(): MonikaState = {
    this.synchronized {
      val stateDBFile = new File(Locations.StateJsonFile)
      ensureFileWritable(stateDBFile)
      if (stateDBFile.exists()) {
        val input = FileUtils.readFileToString(stateDBFile, "UTF-8")
        readStateFromInput(input)
      } else MonikaState()
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
    val json = Try(parseJSON(input)).orElseX(ex =>{
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
    printCompact(renderJSON(json), writer)
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
    def jsonToAction(json: JValue): Action = {
      val name = (json \ "name").extract[String]
      name match {
        case "set-profile" => SetProfile(jsonToProfile(json \ "profile"))
        case "unlock" => Unlock
        case "disable-login" => DisableLogin
      }
    }
    def jsonToRequest(json: JValue): (LocalDateTime, Action) = {
      (LocalDateTime.parse((json \ "time").extract[String]), jsonToAction(json \ "action"))
    }
    MonikaState(
      queue = (json \ "queue").extract[Vector[JValue]].map(jsonToRequest),
      proxy = jsonToProxy(json \ "proxy")
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
    def actionToJson(effect: Action): JValue = {
      effect match {
        case a: SetProfile => ("name" -> "set-profile") ~ ("profile" -> profileToJson(a.profile))
        case Unlock => "name" -> "unlock"
        case DisableLogin => "name" -> "disable-login"
      }
    }
    def requestToJson(request: (LocalDateTime, Action)): JValue = {
      ("time" -> request._1.toString) ~
      ("action" -> actionToJson(request._2))
    }
    ("queue" -> state.queue.map(requestToJson)) ~
    ("proxy" -> proxyToJson(state.proxy))
  }

}
