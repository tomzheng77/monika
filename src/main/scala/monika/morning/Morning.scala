package monika.morning

import monika.server.Constants
import spark.Spark

object Morning {

  // - run an HTTP server
  // - user can solve a captcha at any time, the timestamp will be recorded
  // - user can deposit a note at any time
  // - at each day 7 AM, if there are no captcha solves in the previous hour, then
  //   all notes will be deleted
  // - notes cannot be viewed from 11 PM until 7 AM

  def main(args: Array[String]): Unit = {
    Spark.port(Constants.InterpreterPort)
    Spark.get("/captcha", (req, resp) => {
      // TODO: provide a CAPTCHA image for the user to solve
      ""
    })
    Spark.get("/add-note", (req, resp) => {
      // TODO: remember the note
      ""
    })
    Spark.get("/view-notes", (req, resp) => {
      // TODO: print out all notes
      ""
    })
    Spark.get("/remove-note", (req, resp) => {
      // TODO: remove the note
      ""
    })
  }

}
