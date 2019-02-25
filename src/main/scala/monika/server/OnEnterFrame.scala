package monika.server

import java.time.LocalDateTime
import java.util.{Timer, TimerTask}

import com.mashape.unirest.http.Unirest
import monika.Constants
import monika.orbit.OrbitEncryption
import monika.server.Structs.FutureAction
import monika.server.script.ScriptServer.runScriptFromPoll
import monika.server.subprocess.Subprocess
import org.json4s.JsonAST.JNull
import org.json4s.{DefaultFormats, Formats, JValue}
import org.json4s.jackson.JsonMethods.{parse, pretty, render}
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods

import scala.util.{Failure, Success, Try}

object OnEnterFrame extends UseLogger with OrbitEncryption with UseScalaz with UseDateTime {

  private val timer = new Timer()
  private var hasPollStarted = false

  private var notifiedAction: Set[LocalDateTime] = _
  private var notifiedConfirm: Set[(String, LocalDateTime)] = _

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
        if (!notifiedAction(act.at)) {
          notifiedAction += act.at
          Subprocess.sendNotify(act.script.name, "will run in 1 minute")
        }
      }
      pollAndNotifyOrbit(nowTime) match {
        case Success(_) ⇒
        case Failure(ex) ⇒ LOGGER.error(s"failed to poll orbit: ${ex.getClass.getSimpleName} ${ex.getMessage}")
      }
      (state.copy(queue = state.queue.dropWhile(shouldRun)), state.queue.takeWhile(shouldRun))
    })
    // run each item that was popped
    for (FutureAction(_, script, args) <- maybeRun) runScriptFromPoll(script, args)
  }

  private def pollAndNotifyOrbit(nowTime: LocalDateTime): Try[Unit] = Try {
    val json: JValue = Unirest
      .post(s"http://${Constants.OrbitAddress}:${Constants.OrbitPort}/")
      .body(encryptPBE(pretty(Vector("list-confirms"))))
      .asString().getBody |> decryptPBE |> (s ⇒ parse(s))

    implicit val formats: Formats = DefaultFormats
    for (confirmJson ← json.extract[Seq[JValue]]) {
      val name: String = (confirmJson \ "name").extract[String]
      val time: LocalDateTime = (confirmJson \ "time").extract[String] |> parseDateTime |> (_.get)
      val window: Int = (confirmJson \ "window").extract[Int]
      val key: Boolean = (confirmJson \ "key").extract[Boolean]

      if (nowTime.isAfter(time.minusMinutes(window))) {
        if (!notifiedConfirm(name → time)) {
          notifiedConfirm += name → time
          Subprocess.sendNotify(s"Confirm $name", s"in ${nowTime.untilHMS(time)}" + {
            if (key) " (requires key)" else ""
          })
        }
      }
    }
  }

}
