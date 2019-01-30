package monika.morning

import java.time.LocalDateTime

import scalaz.{@@, Tag}
import spark.Spark.{get, port}
import scalaz.syntax.id._

import scala.language.implicitConversions

object Morning {

  // - run an HTTP server
  // - user can solve a captcha at any time, the timestamp will be recorded
  // - user can deposit a note at any time
  // - at each day 7 AM, if there are no captcha solves in the previous hour, then
  //   all notes will be deleted
  // - notes cannot be viewed from 11 PM until 7 AM

  // - is there any benefit in allowing binary notes?

  sealed trait NoteID
  def NoteID[A <: Int](a: A): A @@ NoteID = Tag[A, NoteID](a)

  sealed trait Message
  def Message[A <: Int](a: A): A @@ Message = Tag[A, Message](a)

//  sealed trait NoteID
//  def NoteID[A](a: A): A @@ NoteID = Tag[A, NoteID](a)

  private var nextID: Int @@ NoteID = NoteID(0)
  private var solves: Vector[LocalDateTime] = Vector.empty
  private var deposits: Map[Int @@ NoteID, String @@ Message] = Map.empty

  def main(args: Array[String]): Unit = {
    port(3000)
    get("/captcha", (req, resp) => {
      Morning.this.synchronized {
        solves :+= LocalDateTime.now()
        "the captcha has been solved"
      }
    })
    get("/deposit", (req, resp) => {
      Morning.this.synchronized {
        deposits = deposits.updated(nextID, Message(req.body()))
        nextID = NoteID(Tag.unwrap(nextID) + 1)
        "the note has been successfully deposited"
      }
    })
    get("/list", (req, resp) => {
      Morning.this.synchronized {
        deposits.toVector.sortBy(_._1).map {
          case (id, bytes) => id
        }
      }
    })
    get("/obtain", (req, resp) => {
      Morning.this.synchronized {
        val id = req.queryParams("id").toInt
        deposits.get(NoteID(id)).map(Tag.unwrap).getOrElse(s"the ID $id does not exist")
      }
    })
    get("/delete", (req, resp) => {
      Morning.this.synchronized {
        deposits -= nextID
        "the item has been successfully deleted"
      }
    })
  }

}
