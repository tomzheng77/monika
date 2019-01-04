package monika.server

import java.util.{Timer, TimerTask}

import monika.server.Environment.rejectOutgoingHttp
import monika.server.Interpreter.startHttpListener

object Monika {

  def setInterval(fn: () => Unit, ms: Int): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = fn()
    }, 0, ms)
  }

  def main(args: Array[String]): Unit = {
    rejectOutgoingHttp(forUser = Constants.ProfileUser)
    setInterval(() => Interpreter.runTransaction(Interpreter.clearActiveOrApplyNext()), 5000)
    startHttpListener()
  }

}
