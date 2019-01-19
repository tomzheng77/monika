package monika.server.script

import java.io.{PrintWriter, StringWriter}
import java.time.LocalDateTime
import java.util.{Timer, TimerTask}

import monika.Primitives.{FileName, FilePath}
import monika.server.Structs.FutureAction
import monika.server._
import monika.server.proxy.{Filter, ProxyServer}
import monika.server.script.property.{Internal, RootOnly}
import monika.server.subprocess.Commands.Command
import monika.server.subprocess.Subprocess
import monika.server.subprocess.Subprocess.CommandOutput
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

    override def activeProfiles(): Map[String, Structs.Profile] = Configuration.readProfileDefinitions()
    override def nowTime(): LocalDateTime = initialTime
    override def printLine(str: String): Unit = writer.println(str)
    override def enqueue(at: LocalDateTime, script: Script, args: Vector[String]): Unit = {
      newFutureActions += FutureAction(at, script, args)
    }
    override def call(command: Command, args: String*): CommandOutput = Subprocess.call(command, args: _*)
    override def query(): Structs.MonikaState = Hibernate.readStateOrDefault()
    override def transaction[A](fn: Structs.MonikaState => (Structs.MonikaState, A)): A = Hibernate.transaction(fn)
    override def restartProxy(filter: Filter): Unit = ProxyServer.startOrRestart(filter)
    override def rewriteCertificates(): Unit = ProxyServer.writeCertificatesToFiles()
    override def findExecutableInPath(name: String @@ FileName): Option[String @@ FilePath] = {
      Subprocess.findExecutableInPath(name)
    }

    def consoleOutput(): String = {
      writer.flush()
      message.toString
    }
    def futureActions(): Vector[FutureAction] = newFutureActions.toVector

  }

  private def runScript(script: String, args: Vector[String]): String = {
    LOGGER.debug(s"received command request: $script ${args.mkString(" ")}")
    val hasRoot = Hibernate.readStateOrDefault().root
    PublicScripts.get(script) match {
      case None => s"unknown command '$script'"
      case Some(c) if c.hasProperty(Internal) => "this is an internal command"
      case Some(c) if c.hasProperty(RootOnly) && !hasRoot => "this command requires root"
      case Some(c) =>
        val api = new DefaultScriptAPI()
        c.run(args)(api)
        enqueueAll(api.futureActions())
        api.consoleOutput()
    }
  }

  private def runScriptInternal(script: Script, args: Vector[String]): Unit = {
    LOGGER.debug(s"run internal: $script ${args.mkString(" ")}")
    val api = new DefaultScriptAPI()
    script.run(args)(api)
    enqueueAll(api.futureActions())
    LOGGER.debug(api.consoleOutput())
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
