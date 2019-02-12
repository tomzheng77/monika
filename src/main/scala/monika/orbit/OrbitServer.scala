package monika.orbit

import java.security.SecureRandom
import java.time.LocalDateTime

import monika.server.{UseDateTime, UseJSON, UseLogger, UseTry}
import org.json4s.JsonAST.JNull
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Formats, JValue}
import scalaz.effect.IO
import spark.Spark

object OrbitServer extends OrbitEncryption with UseLogger with UseDateTime with UseJSON with UseTry {

  private implicit val defaultFormats: Formats = DefaultFormats

  private var notes: Vector[String] = Vector.empty
  private var verifications: Map[String, LocalDateTime] = Map.empty

  def main(args: Array[String]): Unit = {
    Spark.port(8080)
    Spark.post("/orbit", (req, resp) ⇒ {
      resp.header("content-type", "application/json")
      val body = decryptAES(req.body())
      val requestJson: JValue = JsonMethods.parseOpt(body).getOrElse(JNull)
      val responseJson: JValue = handle(requestJson).unsafePerformIO()
      encryptAES(JsonMethods.pretty(JsonMethods.render(responseJson)))
    })
    new Thread(() ⇒ {
      while (true) {
        val nowTime = LocalDateTime.now()
        if (verifications.values.exists(d ⇒ d.isBefore(nowTime))) {
          notes = Vector.empty
          verifications = Map.empty
          LOGGER.info("clearing all notes")
        }
        Thread.sleep(1000)
      }
    }).start()
  }

  private def randomCode(): IO[String] = IO {
    val secure = new SecureRandom()
    val dictionary = ('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9')
    Stream.continually(secure.nextInt(dictionary.size)).map(dictionary).take(16).mkString
  }

  private def requestVerify(date: LocalDate, time: LocalTime): IO[String] = for {
    code ← randomCode()
  } yield {
    val dateAndTime = LocalDateTime.of(date, time)
    verifications = verifications.updated(code, dateAndTime)
    s"you must verify with $code before $dateAndTime"
  }

  def handle(json: JValue): IO[JValue] = {
    val action = (json \ "action").extract[String]
    val message: IO[String] = action match {
      case "append" ⇒ {
        val noteOpt = (json \ "note").extractOpt[String]
        noteOpt match {
          case None ⇒ IO("please enter a note")
          case Some(note) ⇒ IO {
            notes = notes :+ note
            s"the note (id: ${notes.length - 1}) has been successfully added"
          }
        }
      }
      case "list" ⇒ IO {
        notes.zipWithIndex.map(pair ⇒ s"${pair._2}\t| ${pair._1}").mkString("\n")
      }
      case "pending" ⇒ IO {
        verifications.toList.sortBy(_._2).map {
          case (code, time) ⇒ code.take(4) + code.drop(4).map(_) + ": " + time.format()
        } mkString "\n"
      }
      case "remove" ⇒ {
        val indexOpt = (json \ "index").extractOpt[Int]
        indexOpt match {
          case None ⇒ IO("the index is invalid")
          case Some(i) if !notes.indices.contains(i) ⇒ IO(s"the index must be between 0 and ${notes.size - 1} (inclusive)")
          case Some(index) ⇒ IO {
            notes = notes.take(index) ++ notes.drop(index + 1)
            s"the note (id: $index) has been successfully removed"
          }
        }
      }
      case "verify" ⇒ {
        val codeOpt = (json \ "code").extractOpt[String]
        codeOpt match {
          case None ⇒ IO("please enter a code")
          case Some(code) if !verifications.contains(code) ⇒ IO("the code is not found")
          case Some(code) ⇒ IO {
            verifications -= code
            s"the verification ($code) has been accepted"
          }
        }
      }
      case "request-verify" ⇒ {
        val dateTry = (json \ "date").extract[String] |> parseDate
        val timeTry = (json \ "time").extract[String] |> parseTime
        (dateTry, timeTry) match {
          case (Failure(_), Failure(_)) ⇒ IO("the date and time is invalid")
          case (Failure(_), _) ⇒ IO("the date is invalid")
          case (_, Failure(_)) ⇒ IO("the time is invalid")
          case (Success(date), Success(time)) ⇒ requestVerify(date, time)
        }
      }
    }
    message.map("message" → _)
  }

}
