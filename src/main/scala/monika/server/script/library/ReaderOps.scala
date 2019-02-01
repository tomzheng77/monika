package monika.server.script.library

import java.time.LocalDateTime

import monika.Primitives._
import monika.server.Structs.MonikaState
import monika.server.UseScalaz
import monika.server.proxy.Filter
import monika.server.script.Script
import monika.server.subprocess.Commands.Command
import monika.server.subprocess.Subprocess.CommandOutput
import scalaz.@@

import scala.collection.{GenIterable, mutable}
import scala.language.implicitConversions

trait ReaderOps extends UseScalaz {

  type ScriptAPI = monika.server.script.ScriptAPI
  type SC[A] = Reader[ScriptAPI, A]
  protected implicit def SC[A](fn: ScriptAPI => A): SC[A] = Reader(fn)

  def nowTime(): SC[LocalDateTime] = SC(api => api.nowTime())
  def printLine(text: String): SC[Unit] = SC(api => api.printLine(text))
  def call(command: Command, args: String*): SC[CommandOutput] = SC(api => api.call(command, args: _*))
  def getState(): SC[MonikaState] = SC(api => api.getState())
  def setState(state: MonikaState): SC[Unit] = SC(api => api.setState(state))
  def restartProxy(filter: Filter): SC[Unit] = SC(api => api.restartProxy(filter))
  def findExecutableInPath(name: String @@ FileName): SC[Vector[String @@ FilePath]] = SC(api => api.findExecutableInPath(name))

  def transformState(fn: MonikaState => MonikaState): SC[Unit] = for {
    state <- getState()
    _ <- setState(fn(state))
  } yield Unit

  def enqueueAfter(at: LocalDateTime, script: Script, args: Vector[String] = Vector.empty): SC[Unit] = {
    SC(api => api.enqueueAfter(at, script, args))
  }

  def enqueueNextStep(script: Script, args: Vector[String] = Vector.empty): SC[Unit] = SC(api => {
    val time = nowTime()(api)
    enqueueAfter(time, script, args)(api)
  })

  def setNewFilter(filter: Filter): SC[Unit] = steps(
    restartProxy(filter),
    transformState(state => state.copy(filter = filter))
  )

  implicit def anyAsUnit[A](sc: SC[A]): SC[Unit] = sc.map(_ => Unit)

  def setAsNonRoot(): SC[Unit] = transformState(state => state.copy(root = false))

  def steps[A](scs: SC[A]*): SC[Vector[A]] = sequence(scs)
  def sequence[A](scs: GenIterable[SC[A]]): SC[Vector[A]] = SC(api => {
    var buffer = mutable.Buffer[A]()
    for (sc <- scs) {
      buffer += sc(api)
    }
    buffer.toVector
  })

}
