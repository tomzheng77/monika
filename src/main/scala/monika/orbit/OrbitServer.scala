package monika.orbit

import java.security.SecureRandom
import java.time.{LocalDate, LocalDateTime, LocalTime}

import monika.server.{UseDateTime, UseJSON, UseLogger}
import org.json4s.JsonAST.JNull
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Formats, JValue}
import scalaz.effect.IO
import spark.Spark

object OrbitServer extends OrbitEncryption with UseLogger with UseDateTime with UseJSON {

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

  private def today(): IO[LocalDate] = IO(LocalDate.now())
  private def requestVerify(dateOpt: Option[LocalDate], time: LocalTime): IO[String] = for {
    date ← dateOpt.map(IO(_)).getOrElse(today())
    code ← randomCode()
  } yield {
    val dateAndTime = LocalDateTime.of(date, time)
    verifications = verifications.updated(code, dateAndTime)
    s"you must verify with $code before $dateAndTime"
  }

  def handle(json: JValue): IO[JValue] = {
    val action = (json \ "action").extractOpt[String].getOrElse("list")
    val message: IO[String] = action match {
      case "append" ⇒ {
        val note = (json \ "note").extractOpt[String].getOrElse("empty note")
        notes = notes :+ note
        IO(s"the note (id: ${notes.length - 1}) has been successfully added")
      }
      case "list" ⇒ IO(notes.zipWithIndex.map(pair ⇒ s"${pair._2}\t| ${pair._1}").mkString("\n"))
      case "remove" ⇒ {
        val index = (json \ "index").extractOpt[Int].getOrElse(0)
        notes = notes.take(index) ++ notes.drop(index + 1)
        IO(s"the note (id: $index) has been successfully removed")
      }
      case "verify" ⇒ {
        val code = (json \ "code").extract[String]
        if (!verifications.contains(code)) {
          IO("the code is not found")
        } else {
          verifications -= code
          IO(s"the verification ($code) has been accepted")
        }
      }
      case "request-verify" ⇒ {
        val date = (json \ "date").extractOpt[String].flatMap(parseDate(_).toOption)
        val time = (json \ "time").extractOpt[String].flatMap(parseTime(_).toOption)
        requestVerify(date, time.get)
      }
    }
    message.map("message" → _)
  }

}
