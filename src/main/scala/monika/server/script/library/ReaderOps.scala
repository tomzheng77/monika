package monika.server.script.library

import java.time.LocalDateTime

import monika.server.Structs.{MonikaState, Profile}
import monika.server.UseScalaz
import monika.server.proxy.Filter
import monika.server.script.Script
import monika.server.subprocess.Commands.Command
import monika.server.subprocess.Subprocess.CommandOutput
import monika.Primitives._
import scalaz.@@

import scala.collection.{GenIterable, mutable}

trait ReaderOps extends UseScalaz {

  type ScriptAPI = monika.server.script.ScriptAPI
  type SC[A] = Reader[ScriptAPI, A]
  protected implicit def SC[A](fn: ScriptAPI => A): SC[A] = Reader(fn)

  def activeProfiles(): SC[Map[String, Profile]] = SC(api => api.activeProfiles())
  def nowTime(): SC[LocalDateTime] = SC(api => api.nowTime())
  def printLine(text: String): SC[Unit] = SC(api => api.printLine(text))
  def enqueue(at: LocalDateTime, script: Script, args: List[String] = List.empty): SC[Unit] = SC(api => api.enqueue(at, script, args))
  def call(command: Command, args: String*): SC[CommandOutput] = SC(api => api.call(command, args: _*))
  def query(): SC[MonikaState] = SC(api => api.query())
  def update(fn: MonikaState => MonikaState): SC[Unit] = transaction(state => (fn(state), Unit))
  def transaction[A](fn: MonikaState => (MonikaState, A)): SC[A] = SC(api => api.transaction(fn))
  def restartProxy(filter: Filter): SC[Unit] = SC(api => api.restartProxy(filter))
  def findExecutableInPath(name: String @@ FileName): SC[Option[String @@ FilePath]] = SC(api => api.findExecutableInPath(name))

  def setNewProxy(filter: Filter): SC[Unit] = SC(api => {
    api.restartProxy(filter)
    api.update(state => state.copy(filter = filter))
  })

  def setAsNonRoot(): SC[Unit] = SC(api => api.update(state => state.copy(root = false)))

  def sequence[A](scs: GenIterable[SC[A]]): SC[Vector[A]] = SC(api => {
    var buffer = mutable.Buffer[A]()
    for (sc <- scs) {
      buffer += sc(api)
    }
    buffer.toVector
  })

}
