package monika.morning

import java.time.temporal.ChronoUnit
import java.time.{LocalDateTime, LocalTime}
import java.util.{Timer, TimerTask}

import spark.Spark.{get, port}

import scala.language.implicitConversions
import scala.util.Try

object Morning {

  // - run an HTTP server
  // - user can solve a captcha at any time, the timestamp will be recorded
  // - user can deposit a note at any time
  // - at each day 7 AM, if there are no captcha solves in the previous hour, then
  //   all notes will be deleted
  // - notes cannot be viewed from 11 PM until 7 AM

  private var nextID: Int = 0
  private var solves: Vector[LocalDateTime] = Vector.empty
  private var deposits: Map[Int, String] = Map.empty

  def startHttpServer(): Unit = {
    port(3000)
    get("/captcha", (req, resp) => {
      Morning.this.synchronized {
        solves :+= LocalDateTime.now()
        "the captcha has been solved"
      }
    })
    get("/deposit", (req, resp) => {
      Morning.this.synchronized {
        deposits = deposits.updated(nextID, req.body())
        nextID += 1
        "the note has been successfully deposited"
      }
    })
    get("/list", (req, resp) => {
      Morning.this.synchronized {
        deposits.toVector.sortBy(_._1).map {
          case (id, message) => s"$id: $message"
        }.mkString("\n")
      }
    })
    get("/obtain", (req, resp) => {
      Morning.this.synchronized {
        if (req.queryParams("id") == null) "please provide an ID"
        else if (Try(req.queryParams("id").toInt).isFailure) "ID must be an integer"
        else {
          val id = req.queryParams("id").toInt
          deposits.get(id) match {
            case None => s"the ID $id does not exist"
            case Some(message) => message
          }
        }
      }
    })
    get("/delete", (req, resp) => {
      Morning.this.synchronized {
        if (req.queryParams("id") == null) "please provide an ID"
        else if (Try(req.queryParams("id").toInt).isFailure) "ID must be an integer"
        else {
          val id = req.queryParams("id").toInt
          if (!deposits.contains(id)) s"the ID $id does not exist"
          else {
            deposits -= id
            s"the note with id $id has been deleted"
          }
        }
      }
    })
  }

  def runEveryDay(time: LocalTime, task: () => Unit, repeat: Boolean = true): Unit = {
    val timer = new Timer()
    val now = LocalDateTime.now()
    val tomorrow = now.toLocalDate.plusDays(1)
    val tomorrowTime = LocalDateTime.of(tomorrow, time)
    val firstDelay = now.until(tomorrowTime, ChronoUnit.MILLIS)
    val interval = 24 * 60 * 60 * 1000
    timer.schedule(new TimerTask {
      override def run(): Unit = task()
    }, firstDelay, interval)
  }

  def main(args: Array[String]): Unit = {
    startHttpServer()
    runEveryDay(LocalTime.of(7, 0), () => ())
  }

}
