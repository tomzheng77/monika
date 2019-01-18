package monika.server

import java.io.{ByteArrayOutputStream, File, OutputStreamWriter, StringWriter}

import monika.Primitives._
import monika.server.Constants.Locations
import monika.server.Structs._
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
      val writer = new StringWriter()
      writeItemAsJSON(newState, writer)
      FileUtils.writeStringToFile(stateDBFile, writer.toString, "UTF-8"); returnValue
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
    Try(readJSONToItem[MonikaState](input)).orElseX(ex => {
      val message = s"failed to deserialize JSON ${Locations.StateJsonFile}"
      LOGGER.error(message, ex)
      throw new RuntimeException(message, ex)
    })
  }

}
