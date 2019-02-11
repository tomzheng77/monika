package monika.orbit

import java.security.SecureRandom
import java.time.LocalDateTime

import org.json4s.{DefaultFormats, Formats, JValue}
import org.json4s.JsonAST.JNull
import org.json4s.jackson.JsonMethods
import scalaz.effect.IO
import spark.Spark
import org.json4s.JsonDSL._

import scala.util.Random

object OrbitServer extends OrbitEncryption {

  private implicit val defaultFormats: Formats = DefaultFormats

  private var notes: Vector[String] = Vector.empty
  private var verifications: Map[String, LocalDateTime] = Map.empty

  def main(args: Array[String]): Unit = {
    Spark.port(8080)
    Spark.post("/orbit", (req, resp) ⇒ {
      resp.header("content-type", "application/json")
      val body = decrypt(req.body())
      val requestJson: JValue = JsonMethods.parseOpt(body).getOrElse(JNull)
      val responseJson: JValue = handle(requestJson).unsafePerformIO()
      encrypt(JsonMethods.pretty(JsonMethods.render(responseJson)))
    })
  }

  def randomCode(): IO[String] = IO {
    val secure = new SecureRandom()
    val dictionary = ('A' to 'Z') ++ ('a' to 'z') ++ ('0' to '9')
    Stream.continually(secure.nextInt(dictionary.size)).map(dictionary).take(16).mkString
  }

  def handle(json: JValue): IO[JValue] = IO {
    val action = (json \ "action").extractOpt[String].getOrElse("list")
    val message = action match {
      case "append" ⇒ {
        val note = (json \ "note").extractOpt[String].getOrElse("empty note")
        notes = notes :+ note
        s"the note (id: ${notes.length - 1}) has been successfully added"
      }
      case "list" ⇒ notes.zipWithIndex.map(pair ⇒ s"${pair._2}\t| ${pair._1}").mkString("\n")
      case "remove" ⇒ {
        val index = (json \ "index").extractOpt[Int].getOrElse(0)
        notes = notes.take(index) ++ notes.drop(index + 1)
        s"the note has (id: $index) been successfully removed"
      }
      case "verify" ⇒ {
        val code = (json \ "code").extract[String]
        verifications -= code
        s"the verification ($code) has been accepted"
      }
      case "request-verify" ⇒ {
        val date = (json \ "date").extractOpt[String]
        val time = (json \ "time").extractOpt[String]
        val code = (json \ "code").extractOpt[String]
        "you must verify before <10> otherwise all notes will be lost"
      }
    }
    ("message" → message)
  }

}
