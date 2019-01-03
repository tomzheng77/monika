package monika

import java.util.{Timer, TimerTask}

object Monika {

  def onSecondTick(): Unit = {
    Interpreter.runTransaction(Interpreter.clearActiveOrApplyNext())
  }

  def scheduleOnSecondTick(): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = onSecondTick()
    }, 0, 1000)
  }

  def main(args: Array[String]): Unit = {
    Firewall.rejectOutgoingHttp(forUser = Constants.ProfileUser)
    scheduleOnSecondTick()
    Interpreter.startHttpServer()
  }

}
