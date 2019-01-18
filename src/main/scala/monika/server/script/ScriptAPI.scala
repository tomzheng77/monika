package monika.server.script

import java.time.LocalDateTime

import monika.Primitives.{FileName, FilePath}
import monika.server.Structs.{MonikaState, Profile}
import monika.server.proxy.Filter
import monika.server.subprocess.Commands.Command
import monika.server.subprocess.Subprocess.CommandOutput
import scalaz.@@

trait ScriptAPI {

  def activeProfiles(): Map[String, Profile]

  def nowTime(): LocalDateTime

  def printLine(text: String): Unit

  /**
    * - runs another script at the specified time
    * - if the time is before now, it will be run immediately after the current script has completed
    */
  def enqueue(at: LocalDateTime, script: Script, args: Vector[String] = Vector.empty)

  /**
    * - executes a shell command and returns the output
    */
  def call(command: Command, args: String*): CommandOutput
  def query(): MonikaState
  def update(fn: MonikaState => MonikaState): Unit = transaction(state => (fn(state), Unit))
  def transaction[A](fn: MonikaState => (MonikaState, A)): A

  /**
    * - restarts the proxy with the given filter
    */
  def restartProxy(filter: Filter): Unit
  def rewriteCertificates(): Unit

  def findExecutableInPath(name: String @@ FileName): Option[String @@ FilePath]

}
