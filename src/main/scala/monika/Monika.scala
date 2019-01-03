package monika

import java.util.{Timer, TimerTask}

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
    Firewall.rejectOutgoingHttp(forUser = Constants.ProfileUser)
    scheduleTimer()
    Interpreter.startHttpServer()
  }

}
