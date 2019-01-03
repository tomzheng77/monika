package monika

import java.util.{Timer, TimerTask}

object Monika {

  def onSecondTick(): Unit = {
  }

  def initFirstState(): Unit = {
    val at = Storage.transaction(state => (state, state.active))
    at match {
      case None =>
      case Some(item) => {
        item.startTime
        item.endTime
        item.profile
      }
    }
  }

  def scheduleOnSecondTick(): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = onSecondTick()
    }, 0, 1000)
  }

  def main(args: Array[String]): Unit = {
    Firewall.rejectOutgoingHttp(forUser = Constants.ProfileUser)
    initFirstState()
    scheduleOnSecondTick()
    Interpreter.startHttpServer()
  }

}
