package monika.server.action

import java.time.LocalDateTime
import java.util.{Timer, TimerTask}

import monika.server.Structs.{Action, ClearAllRestrictions, DisableLogin, RestrictProfile}
import monika.server.{LittleProxy, Persistence, UseLogger, UserControl}

object Performer extends UseLogger {

  def pollQueueAutomatically(interval: Int = 1000): Unit = {
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
    }, 0, interval)
  }

  private def performAction(action: Action): Unit = {
    LOGGER.debug(s"performing action: ${action.getClass.getSimpleName}")
    action match {
      case DisableLogin => UserControl.restrictLogin()
      case ClearAllRestrictions => UserControl.clearAllRestrictions()
      case RestrictProfile(profile) =>
        LittleProxy.startOrRestart(profile.proxy)
        UserControl.removeFromWheelGroup()
        UserControl.restrictProgramsExcept(profile.programs)
        UserControl.restrictProjectsExcept(profile.projects)
      case other =>
    }
  }

}
