package monika.server

import java.util.{Timer, TimerTask}

import monika.server.Environment.rejectOutgoingHttp

object Monika {

  def scheduleTimer(): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = {
        Interpreter.runTransaction(Interpreter.clearActiveOrApplyNext())
      }
    }, 0, 5000)
  }

  def main(args: Array[String]): Unit = {
    rejectOutgoingHttp(forUser = Constants.ProfileUser)
    scheduleTimer()
    Interpreter.startHttpServer()
  }

}
