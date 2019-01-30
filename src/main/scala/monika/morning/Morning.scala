package monika.morning

import java.time.LocalDateTime

import monika.Primitives.FileName
import monika.server.Constants
import scalaz.{@@, Tag}
import spark.Spark
import spark.Spark.{get, port}

object Morning {

  // - run an HTTP server
  // - user can solve a captcha at any time, the timestamp will be recorded
  // - user can deposit a note at any time
  // - at each day 7 AM, if there are no captcha solves in the previous hour, then
  //   all notes will be deleted
  // - notes cannot be viewed from 11 PM until 7 AM


  sealed trait ItemID
  def ItemID[A](a: A): A @@ ItemID = Tag[A, ItemID](a)

  private var nextID: Int @@ ItemID = ItemID(0)
  private var solves: Vector[LocalDateTime] = Vector.empty
  private var deposits: Map[Int @@ ItemID, Array[Byte]] = Map.empty

  def main(args: Array[String]): Unit = {
    port(Constants.InterpreterPort)
    get("/captcha", (req, resp) => {
      Morning.this.synchronized {
        // TODO: provide a CAPTCHA image for the user to solve
        solves :+= LocalDateTime.now()
        ""
      }
    })
    get("/deposit", (req, resp) => {
      Morning.this.synchronized {
        // TODO: remember the note
        deposits = deposits.updated(nextID, req.bodyAsBytes())
        nextID = ItemID(Tag.unwrap(nextID) + 1)
        ""
      }
    })
    get("/obtain", (req, resp) => {
      Morning.this.synchronized {
        // TODO: print out all notes
        ""
      }
    })
    get("/delete", (req, resp) => {
      Morning.this.synchronized {
        // TODO: remove the note
        ""
      }
    })
  }

}
