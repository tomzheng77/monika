package monika.server

import java.time.LocalDateTime
import java.util.{Timer, TimerTask}

import com.mashape.unirest.http.Unirest
import monika.Constants
import monika.orbit.OrbitEncryption
import monika.server.Structs.Action
import monika.server.script.ScriptServer.runScriptFromPoll
import monika.server.script.property.Mainline
import monika.server.subprocess.Subprocess
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods.{parse, pretty}
import org.json4s.{DefaultFormats, Formats, JValue}

import scala.util.{Failure, Success, Try}

object OnEnterFrame extends UseLogger with OrbitEncryption with UseScalaz with UseDateTime {

  private val timer = new Timer()
  private var hasPollStarted = false
  private var notifiedAction: Set[Action] = Set.empty

  def startPoll(interval: Int = 1000): Unit = {
    this.synchronized {
      if (!hasPollStarted) {
        hasPollStarted = true
        timer.schedule(new TimerTask {
          override def run(): Unit = {
            new Thread(() ⇒ pollAndNotify()).start()
            new Thread(() ⇒ pollAndRunScripts()).start()
          }
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

  private def pollAndRunScripts(): Unit = {
    LOGGER.trace("poll queue")
    // pop items from the head of the queue, save the updated state
    val maybeRun: Vector[Action] = Hibernate.transaction(state => {
      val nowTime = LocalDateTime.now()
      def shouldRun(act: Action): Boolean = !act.at.isAfter(nowTime)
      val runList = state.queue.takeWhile(shouldRun)
      val newPrevious = state.previous.orElse(runList.filter(_.script.hasProperty(Mainline)).lastOption)
      (state.copy(queue = state.queue.dropWhile(shouldRun), previous = newPrevious), runList)
    })
    // run each item that was popped
    for (Action(_, script, args) <- maybeRun) runScriptFromPoll(script, args)
  }

  private def pollAndNotify(): Unit = {
    val state = Hibernate.readStateOrDefault()
    val nowTime = LocalDateTime.now()
    def shouldNotify(act: Action): Boolean = !act.at.isAfter(nowTime.plusMinutes(1))
    for (act ← state.queue.takeWhile(shouldNotify)) {
      if (!notifiedAction(act)) {
        notifiedAction += act
        Subprocess.sendNotify(act.script.name, "will run in 1 minute")
      }
    }
    pollAndNotifyOrbit(nowTime) match {
      case Success(_) ⇒
      case Failure(ex) ⇒ LOGGER.error(s"failed to poll orbit: ${ex.getClass.getSimpleName} ${ex.getMessage}")
    }
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
      val key: Boolean = (confirmJson \ "hasKey").extract[Boolean]

      if (nowTime.isAfter(time.minusMinutes(window))) {
        Subprocess.sendNotify(s"Confirm $name", s"in ${nowTime.untilHMS(time)}" + {
          if (key) " (requires key)" else ""
        })
      }
    }
  }

}
