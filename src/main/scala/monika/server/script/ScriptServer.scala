package monika.server.script

import java.io.{PrintWriter, StringWriter}
import java.time.LocalDateTime
import java.util.{Timer, TimerTask}

import monika.Primitives
import monika.server.Structs.FutureAction
import monika.server.Subprocess.CommandOutput
import monika.server._
import monika.server.proxy.{Filter, ProxyServer}
import scalaz.@@
import spark.Spark

import scala.collection.{GenIterable, mutable}
import scala.util.Try

object ScriptServer extends UseLogger with UseJSON with UseScalaz with UseDateTime {

  private val PublicScripts: Map[String, Script] = Script.allScriptsByName

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
          Try(readJSONToItem[List[String]](cmd)).getOrElse(Nil)
        }
        if (parts.isEmpty) "please provide a command (cmd) in JSON format"
        else runScript(parts.head, parts.tail.toVector)
      })
    }
  }

  private class DefaultScriptAPI() extends ScriptAPI {

    private val initialTime = LocalDateTime.now()
    private val message = new StringWriter()
    private val writer = new PrintWriter(message)
    private val newFutureActions = mutable.Buffer[FutureAction]()

    override def nowTime(): LocalDateTime = initialTime
    override def println(str: String): Unit = writer.println(str)
    override def enqueue(at: LocalDateTime, script: Script, args: List[String]): Unit = {
      newFutureActions += FutureAction(at, script, args.toVector)
    }
    override def call(command: String @@ Primitives.FileName, args: String*): CommandOutput = Subprocess.call(command, args: _*)
    override def query(): Structs.MonikaState = Hibernate.readStateOrDefault()
    override def transaction[A](fn: Structs.MonikaState => (Structs.MonikaState, A)): A = Hibernate.transaction(fn)
    override def restartProxy(filter: Filter): Unit = ProxyServer.startOrRestart(filter)

    def consoleOutput(): String = writer.toString
    def futureActions(): Vector[FutureAction] = newFutureActions.toVector

  }

  private def runScript(script: String, args: Vector[String]): String = {
    LOGGER.debug(s"received command request: $script ${args.mkString(" ")}")
    val hasRoot = Hibernate.readStateOrDefault().root
    PublicScripts.get(script) match {
      case None => s"unknown command '$script'"
      case Some(_: RequireRoot) if !hasRoot => "this command requires root"
      case Some(c) =>
        val api = new DefaultScriptAPI()
        c.run(args)(api)
        val nowTime = LocalDateTime.now()
        val (runLater, runNow) = api.futureActions().sortBy(f => f.at).partition(f => f.at.isAfter(nowTime))
        enqueueAll(runLater)
        for (item <- runNow) {
          runScriptInternal(item.script, item.args)
        }
        api.consoleOutput()
    }
  }

  private def runScriptInternal(script: Script, args: Vector[String]): Unit = {
    val api = new DefaultScriptAPI()
    script.run(args)(api)
  }

  private val timer = new Timer()
  private var hasPollStarted = false

  private def enqueueAll(actions: GenIterable[FutureAction]): Unit = {
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
    for (FutureAction(_, script, args) <- maybeRun) runScriptInternal(script, args)
  }

}
