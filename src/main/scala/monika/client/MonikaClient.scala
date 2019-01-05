package monika.client

import com.mashape.unirest.http.Unirest
import org.json4s.JValue
import org.json4s.native.JsonMethods._
import org.json4s.JsonDSL._

import scala.io.StdIn

object MonikaClient {

  def main(args: Array[String]): Unit = {
    val user = System.getenv("USER")
    assert(user != null, "user cannot be null")
    while (true) {
      val line: String = StdIn.readLine("M1-1> ")
      val parts: JValue = seq2jvalue(line.split(' ').toVector)
      val partsJson: String = pretty(render(parts))
      val partsJson: String Unirest.get(s"http://127.0.0.1:${Constants.InterpreterPort}/request")
        .queryString("cmd", partsJson)
        .asString()
    }
  }

}
