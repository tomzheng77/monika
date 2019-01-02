package monika

import java.util.{Timer, TimerTask}

object Monika {

  def call(args: Array[String]): Unit = {

  }

  def enableFirewallForProfile(): Unit = {
    call("iptables -w 10 -A OUTPUT -p tcp -m owner --uid-owner profile --dport 80 -j REJECT".split(' '))
    call("iptables -w 10 -A OUTPUT -p tcp -m owner --uid-owner profile --dport 443 -j REJECT".split(' '))
  }

  def onSecondTick(): Unit = {

  }

  def main(args: Array[String]): Unit = {
    def scheduleOnSecondTick(): Unit = {
      val timer = new Timer()
      timer.schedule(new TimerTask {
        override def run(): Unit = onSecondTick()
      }, 0, 1000)
    }
    scheduleOnSecondTick()
    enableFirewallForProfile()
  }

}
