package monika.server.script

import java.time.LocalDateTime

import monika.Primitives.FileName
import monika.server.Structs.MonikaState
import scalaz.@@

trait ScriptAPI {

  def println(str: String): Unit

  def run(script: Script, args: List[String] = List.empty)
  def enqueue(at: LocalDateTime, script: Script, args: List[String] = List.empty)

  def call(command: String @@ FileName, args: String*)

  def query(): MonikaState
  def command(fn: MonikaState => MonikaState): Unit = transaction(state => (fn(state), Unit))
  def transaction[A](fn: MonikaState => (MonikaState, A)): A

}
