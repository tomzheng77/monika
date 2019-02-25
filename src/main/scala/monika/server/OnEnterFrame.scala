package monika.server

import java.time.LocalDateTime
import java.util.{Timer, TimerTask}

import monika.orbit.OrbitEncryption
import monika.server.Structs.FutureAction
import monika.server.script.ScriptServer.runScriptFromPoll
import monika.server.subprocess.Subprocess

object OnEnterFrame extends UseLogger with OrbitEncryption {

  private val timer = new Timer()
  private var hasPollStarted = false
  private var notified: Set[LocalDateTime] = _

  def startPoll(interval: Int = 1000): Unit = {
    this.synchronized {
      if (!hasPollStarted) {
        hasPollStarted = true
        timer.schedule(new TimerTask {
          override def run(): Unit = pollQueue()
        }, 0, interval)
      }
    }
  }

  def stopPoll(): Unit = {
    this.synchronized {
      if (hasPollStarted) {
        hasPollStarted = false
        timer.cancel()
      }
    }
  }

  private def pollQueue(): Unit = {
    LOGGER.trace("poll queue")
    // pop items from the head of the queue, save the updated state
    val maybeRun: Vector[FutureAction] = Hibernate.transaction(state => {
      val nowTime = LocalDateTime.now()
      def shouldRun(act: FutureAction): Boolean = !act.at.isAfter(nowTime)
      def shouldNotify(act: FutureAction): Boolean = !act.at.isAfter(nowTime.minusMinutes(1))
      for (act ← state.queue.takeWhile(shouldNotify)) {
        if (!notified(act.at)) {
          notified += act.at
          Subprocess.sendNotify(act.script.name, "will run in 1 minute")
        }
      }
      (state.copy(queue = state.queue.dropWhile(shouldRun)), state.queue.takeWhile(shouldRun))
    })
    // run each item that was popped
    for (FutureAction(_, script, args) <- maybeRun) runScriptFromPoll(script, args)
  }

}
