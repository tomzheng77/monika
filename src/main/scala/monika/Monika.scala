package monika

import java.util.{Timer, TimerTask}

object Monika {

  def onSecondTick(): Unit = {
  }

  def startCommandListener(): Unit = {

  }

  def startHttpProxyServer(): Unit = {

  }

  def main(args: Array[String]): Unit = {
    def scheduleOnSecondTick(): Unit = {
      val timer = new Timer()
      timer.schedule(new TimerTask {
        override def run(): Unit = onSecondTick()
      }, 0, 1000)
    }
    scheduleOnSecondTick()
    Firewall.rejectHttpFromProfile()
    startCommandListener()
    startHttpProxyServer()
  }

}
