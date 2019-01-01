package monika

import java.util.{Timer, TimerTask}

object Monika {

  def onSecondTick(): Unit = {

  }

  def main(args: Array[String]): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = onSecondTick()
    }, 0, 1000)
  }
}
