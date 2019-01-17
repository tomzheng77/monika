package monika.server

import java.io.{ByteArrayOutputStream, File, OutputStream, OutputStreamWriter}
import java.time.LocalDateTime

import monika.Primitives._
import monika.server.Constants.Locations
import monika.server.LittleProxy.ProxySettings
import monika.server.Structs._
import monika.server.signal.Script
import org.apache.commons.io.FileUtils

import scala.util.Try

/**
  * - persists a single MonikaState to disk
  * - provides a method to perform a stateful transaction on MonikaState
  * - reports any errors to log
  */
object Hibernate extends UseLogger with UseJSON with UseScalaz {

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
    def jsonToRequest(json: JValue): FutureAction = {
      FutureAction(
        LocalDateTime.parse((json \ "time").extract[String]),
        (json \ "script").extract[String] |> Script.allScriptsByKey,
        (json \ "args").extract[Vector[String]]
      )
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
    def requestToJson(request: FutureAction): JValue = {
      ("time" -> request.at.toString) ~
      ("script" -> request.script.callKey) ~
      ("args" -> request.args)
    }
    ("queue" -> state.queue.map(requestToJson)) ~
    ("proxy" -> proxyToJson(state.proxy))
  }

}
