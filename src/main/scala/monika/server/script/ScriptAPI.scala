package monika.server.script

import java.time.LocalDateTime

import monika.Primitives.{FileName, FilePath}
import monika.server.Structs.MonikaState
import monika.server.proxy.Filter
import monika.server.subprocess.Commands.Command
import monika.server.subprocess.Subprocess.CommandOutput
import scalaz.@@

trait ScriptAPI {

  def nowTime(): LocalDateTime
  def printLine(text: String): Unit
  def call(command: Command, args: String*): CommandOutput = callWithInput(command, args.toArray, Array.emptyByteArray)
  def callWithInput(command: Command, args: Array[String], input: Array[Byte]): CommandOutput
  def getState(): MonikaState
  def setState(state: MonikaState): Unit
  def restartProxy(filter: Filter): Unit
  def rewriteCertificates(): Unit
  def findExecutableInPath(name: String @@ FileName): Vector[String @@ FilePath]

}
