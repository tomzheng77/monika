package monika.server.script.library

import monika.Primitives._
import monika.server.Structs.{Action, MonikaState}
import monika.server.proxy.Filter
import monika.server.script.Script
import monika.server.subprocess.Commands.Command
import monika.server.subprocess.Subprocess.CommandOutput
import monika.server.{UseDateTime, UseScalaz}
import scalaz.@@

import scala.collection.{GenIterable, mutable}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

trait ReaderOps extends UseScalaz with UseDateTime {

  // an IOS is an external effect which may interact via a ScriptAPI
  type ScriptAPI = monika.server.script.ScriptAPI
  type IOS[A] = Reader[ScriptAPI, A]
  protected implicit def IOS[A](fn: ScriptAPI => A): IOS[A] = Reader(fn)

  def unit[A](a: A): IOS[A] = IOS(_ ⇒ a)
  def nowTime(): IOS[LocalDateTime] = IOS(api => api.nowTime())
  def printLine(text: String): IOS[Unit] = IOS(api => api.printLine(text))
  def call(command: Command, args: String*): IOS[CommandOutput] = IOS(api => api.call(command, args: _*))
  def callWithInput(command: Command, args: Array[String], input: Array[Byte]): IOS[CommandOutput] = IOS(api => api.callWithInput(command, args, input))
  def getState(): IOS[MonikaState] = IOS(api => api.getState())
  def setState(state: MonikaState): IOS[Unit] = IOS(api => api.setState(state))
  def restartProxy(filter: Filter): IOS[Unit] = IOS(api => api.restartProxy(filter))
  def findExecutableInPath(name: String @@ Filename): IOS[Vector[String @@ CanonicalPath]] = IOS(api => api.findExecutableInPath(name))

  def transformState(fn: MonikaState => MonikaState): IOS[Unit] = for {
    state <- getState()
    _ <- setState(fn(state))
  } yield Unit

  def removeActionFromQueue(index: Int): IOS[Unit] = {
    def removeAt[A](index: Int)(queue: Vector[A]): Vector[A] = {
      if (!queue.indices.contains(index)) queue
      else queue.take(index) ++ queue.drop(index + 1)
    }
    IOS(api => {
      val state = api.getState()
      api.setState(state.copy(queue = removeAt(index)(state.queue)))
    })
  }

  def findScriptInQueue[A <: Script](script: A): IOS[Option[(Action, Int)]] = {
    IOS(api ⇒ {
      val state = api.getState()
      state.queue.indexWhere(_.script == script) match {
        case -1 ⇒ None
        case index ⇒ Some(state.queue(index) -> index)
      }
    })
  }

  def addActionToQueue(at: LocalDateTime, script: Script, args: Vector[String] = Vector.empty): IOS[Unit] = {
    IOS(api => {
      val state = api.getState()
      val action = Action(at, script, args)
      api.setState(state.copy(queue = (state.queue :+ action).sortBy(_.at)))
    })
  }

  def addActionToQueueNow(script: Script, args: Vector[String] = Vector.empty): IOS[Unit] = for {
    time <- nowTime()
    _ <- addActionToQueue(time, script, args)
  } yield Unit

  def addActionToQueueAfter(minutes: Int)(script: Script, args: Vector[String] = Vector.empty): IOS[Unit] = for {
    time <- nowTime()
    _ <- addActionToQueue(time.plusMinutes(minutes), script, args)
  } yield Unit

  def setFilter(filter: Filter): IOS[Unit] = steps(
    restartProxy(filter),
    transformState(state => state.copy(filter = filter))
  )

  implicit def anyAsUnit[A](sc: IOS[A]): IOS[Unit] = sc.map(_ => Unit)

  def setAsNonRoot(): IOS[Unit] = transformState(state => state.copy(root = false))
  def setAsRoot(): IOS[Unit] = transformState(state => state.copy(root = true))

  def steps[A](scs: IOS[A]*): IOS[Vector[A]] = sequence(scs)
  def sequence[A](scs: GenIterable[IOS[A]]): IOS[Vector[A]] = IOS(api => {
    var buffer = mutable.Buffer[A]()
    for (sc <- scs) {
      buffer += sc(api)
    }
    buffer.toVector
  })

  def branch[A](condition: IOS[Boolean], ifTrue: IOS[A], ifFalse: IOS[A]): IOS[A] = {
    condition.flatMap(b ⇒ if (b) ifTrue else ifFalse)
  }

  protected def require[A, B](t: ⇒ A)(fil: A ⇒ Boolean)(b: String): Either[IOS[Unit], A] = {
    Try(t).filter(fil) match {
      case Success(a) ⇒ Right(a)
      case Failure(_) ⇒ Left(printLine(b))
    }
  }

  protected def optionalValue[A, B](t: ⇒ A)(fil: A ⇒ Boolean): Option[A] = {
    Try(t).filter(fil).toOption
  }

  protected def check[B](e: Boolean)(b: B): Either[IOS[B], Unit] = {
    if (e) Right(()) else Left(unit(b))
  }

  protected implicit def eitherToST[A, B](either: Either[IOS[A], IOS[A]]): IOS[A] = {
    either.fold(identity, identity)
  }

  protected def pass[A](a: A): Boolean = true
  protected def notEmpty(a: String): Boolean = a.nonEmpty

}
