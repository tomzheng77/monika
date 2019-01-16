package monika.server

import java.time.LocalDateTime
import java.util.{Timer, TimerTask}

import monika.server.Structs.{Action, DisableLogin, SetProfile, Unlock}

object AutomaticQueue extends UseLogger {

  def startPolling(): Unit = {
    def pollQueue(): Unit = {
      LOGGER.debug("poll queue")
      Persistence.transaction(state => {
        val nowTime = LocalDateTime.now()
        state.queue.headOption match {
          case None => (state, Unit)
          case Some((time, _)) if time.isAfter(nowTime) => (state, Unit)
          case Some((_, action)) =>
            performAction(action)
            (state.copy(queue = state.queue.tail), Unit)
        }
      })
    }
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = pollQueue()
    }, 0, 1000)
  }

  private def performAction(action: Action): Unit = {
    LOGGER.debug(s"performing action: ${action.getClass.getSimpleName}")
    action match {
      case DisableLogin => UserControl.restrictLogin()
      case Unlock => UserControl.clearAllRestrictions()
      case SetProfile(profile) =>
        LittleProxy.startOrRestart(profile.proxy)
        UserControl.removeFromWheelGroup()
        UserControl.restrictProgramsExcept(profile.programs)
        UserControl.restrictProjectsExcept(profile.projects)
      case other =>
    }
  }

}
