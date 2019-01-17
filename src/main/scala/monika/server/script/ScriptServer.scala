package monika.server.script

import java.io.{PrintWriter, StringWriter}
import java.time.LocalDateTime
import java.util.{Timer, TimerTask}

import monika.server.Structs.FutureAction
import monika.server._
import spark.Spark

import scala.collection.GenIterable

object ScriptServer extends UseLogger with UseJSON with UseScalaz with UseDateTime {

  private val PublicScripts: Map[String, Script] = Script.allScriptsByKey

  /**
    * - receives commands from the SimpleHttpClient
    * - passes them into the provided handler
    */
  def startListener(): Unit = {
    this.synchronized {
      PublicScripts.foreach(pair => {
        LOGGER.debug(s"found script: ${pair._1}")
      })
      Spark.port(Constants.InterpreterPort)
      Spark.get("/request", (req, resp) => {
        resp.`type`("text/plain") // prevent being intercepted by the proxy
        val parts: List[String] = {
          val cmd: String = Option(req.queryParams("cmd")).getOrElse("")
          parseOptJSON(cmd).flatMap(_.extractOpt[List[String]]).getOrElse(Nil)
        }
        if (parts.isEmpty) "please provide a command (cmd) in JSON format"
        else runScript(parts.head, parts.tail.toVector)
      })
    }
  }

  private def runScript(script: String, args: Vector[String]): String = {
    LOGGER.debug(s"received command request: $script ${args.mkString(" ")}")
    val hasRoot = Hibernate.readStateOrDefault().root
    PublicScripts.get(script) match {
      case None => s"unknown command '$script'"
      case Some(_: RequireRoot) if !hasRoot => "this command requires root"
      case Some(c) =>
        val message = new StringWriter()
        val writer = new PrintWriter(message)
        c.run(args, writer)
        message.toString
    }
  }

  private val timer = new Timer()
  private var hasPollStarted = false

  def enqueue(action: FutureAction): Unit = {
    Hibernate.transaction(state => {
      (state.copy(queue = (state.queue :+ action).sortBy(a => a.at)), Unit)
    })
  }

  def enqueueAll(actions: GenIterable[FutureAction]): Unit = {
    Hibernate.transaction(state => {
      (state.copy(queue = (state.queue ++ actions).sortBy(a => a.at)), Unit)
    })
  }

  def startPoll(interval: Int = 1000): Unit = {
    this.synchronized {
      if (!hasPollStarted) {
        hasPollStarted = true
        timer.schedule(new TimerTask {
          override def run(): Unit = pollQueue()
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

  private def pollQueue(): Unit = {
    LOGGER.debug("poll queue")
    // pop items from the head of the queue, save the updated state
    val maybeRun: Vector[FutureAction] = Hibernate.transaction(state => {
      val nowTime = LocalDateTime.now()
      def shouldRun(act: FutureAction): Boolean = !act.at.isAfter(nowTime)
      (state.copy(queue = state.queue.dropWhile(shouldRun)), state.queue.takeWhile(shouldRun))
    })
    // run each item that was popped
    for (FutureAction(_, script, args) <- maybeRun) {
      val message = new StringWriter()
      val writer = new PrintWriter(message)
      script.run(args, writer)
    }
  }

}
