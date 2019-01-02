package monika

import java.util.{Timer, TimerTask}

object Monika {

  def onSecondTick(): Unit = {
  }

  def startCommandListener(): Unit = {

  }

  def startHttpProxyServer(): Unit = {

  }

  def scheduleOnSecondTick(): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = onSecondTick()
    }, 0, 1000)
  }

  def main(args: Array[String]): Unit = {
    scheduleOnSecondTick()
    Firewall.rejectOutgoingHttp(forUser = Constants.ProfileUser)
    startCommandListener()
    startHttpProxyServer()
  }

}
