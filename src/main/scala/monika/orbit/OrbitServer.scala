package monika.orbit

import java.time.LocalDateTime

import monika.orbit.Domain.OrbitState
import monika.server.{UseDateTime, UseJSON, UseLogger, UseTry}
import org.json4s.jackson.JsonMethods
import org.json4s.{DefaultFormats, Formats}
import spark.Spark

object OrbitServer extends OrbitEncryption with UseLogger with UseDateTime with UseJSON with UseTry {

  private implicit val formats: Formats = DefaultFormats
  private var state: OrbitState = _

  def main(args: Array[String]): Unit = {
    state = Domain.initialState
    Spark.port(9002)
    Spark.post("/", (req, resp) ⇒ {
      resp.header("content-type", "text/plain")
      val body = decryptPBE(req.body())
      val args = JsonMethods.parseOpt(body).flatMap(_.extractOpt[Vector[String]]).getOrElse(Vector.empty)
      val nowTime = LocalDateTime.now()
      val (newState, response) = Domain.handle(args)(nowTime)(state)
      state = newState
      encryptPBE(response)
    })
    new Thread(() ⇒ {
      while (true) {
        val nowTime = LocalDateTime.now()
        for (confirm ← state.confirms.find(c ⇒ c.time.isBefore(nowTime))) {
          state = Domain.initialState
          LOGGER.info(s"failed confirm ${confirm.name} at ${confirm.time.format()}")
        }
        Thread.sleep(1000)
      }
    }).start()
  }

}
