package monika.server.action

import java.time.LocalDateTime
import java.util.{Timer, TimerTask}

import monika.server.Structs._
import monika.server._

import scala.collection.GenIterable

object Performer extends UseLogger with UseDateTime {

  private val timer = new Timer()
  private var hasPollStarted = false

  def enqueueAll(actions: GenIterable[FutureAction]): Unit = {
    Persistence.transaction(state => {
      (state.copy(queue = (state.queue ++ actions).sortBy(a => a.at)), Unit)
    })
  }

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
    LOGGER.debug("poll queue")
    Persistence.transaction(state => {
      val nowTime = LocalDateTime.now()
      state.queue.headOption match {
        case None => (state, Unit)
        case Some(FutureAction(time, _)) if time.isAfter(nowTime) => (state, Unit)
        case Some(FutureAction(_, action)) =>
          performAction(action)
          (state.copy(queue = state.queue.tail), Unit)
      }
    })
  }

  def performAction(action: Action): Unit = {
    LOGGER.debug(s"performing action: ${action.getClass.getSimpleName}")
    action match {
      case DisableLogin => Restrictions.restrictLogin()
      case ClearAllRestrictions => Restrictions.clearAllRestrictions()
      case RestrictProfile(profile) =>
        LittleProxy.startOrRestart(profile.proxy)
        Restrictions.removeFromWheelGroup()
        Restrictions.restrictProgramsExcept(profile.programs)
        Restrictions.restrictProjectsExcept(profile.projects)
      case other =>
    }
  }

}
