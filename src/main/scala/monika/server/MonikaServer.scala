package monika.server

import monika.Constants._
import monika.server.Structs._
import monika.server.proxy.ProxyServer
import monika.server.script._
import monika.server.subprocess.Commands._
import monika.server.subprocess.Subprocess._
import org.apache.commons.lang3.SystemUtils
import org.apache.log4j._
import scalaz.Tag

object MonikaServer extends UseLogger {

  def main(args: Array[String]): Unit = {
    logToFileAndConsole()
    LOGGER.info("M.O.N.I.K.A starting...")
    LOGGER.logIfFail("error while starting") {
      checkOSEnvironment()
      rejectOutgoingHttp()

      val initialState: MonikaState = Hibernate.readStateOrDefault()
      ProxyServer.startOrRestart(initialState.filter)
      OnEnterFrame.startPoll()
      ScriptServer.startListener()
    }
    LOGGER.info("M.O.N.I.K.A started")
  }

  /**
    * -
    * - exit if a critical requirement is not met
    */
  private def checkOSEnvironment(): Unit = {
    if (!SystemUtils.IS_OS_LINUX) {
      LOGGER.error("system is not linux")
      System.exit(1)
    }
    if (System.getenv("USER") != "root") {
      LOGGER.error("user is not root")
      System.exit(2)
    }
    val cannotExecute = CommandArray.map(c => c.name).filter(findExecutableInPath(_).isEmpty)
    for (program <- cannotExecute) {
      val programName = Tag.unwrap(program)
      LOGGER.error(s"cannot find executable program: $programName")
    }
    if (cannotExecute.nonEmpty) {
      System.exit(3)
    }
  }

  private def rejectOutgoingHttp(): Unit = {
    val forUser: String = MonikaUser
    // NOTE: extra 'iptables' argument passed in case of xtables-legacy-multi
    val out1 = callUnsafe("iptables", s"iptables -w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 80 -j REJECT".split(' '))
    LOGGER.debug(new String(out1.stdout, "UTF-8"))
    LOGGER.debug(new String(out1.stderr, "UTF-8"))
    val out2 = callUnsafe("iptables", s"iptables -w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 443 -j REJECT".split(' '))
    LOGGER.debug(new String(out2.stdout, "UTF-8"))
    LOGGER.debug(new String(out2.stderr, "UTF-8"))
  }

  private def logToFileAndConsole(): Unit = {
    // https://www.mkyong.com/logging/log4j-log4j-properties-examples/
    // https://stackoverflow.com/questions/8965946/configuring-log4j-loggers-programmatically
    val console = new ConsoleAppender()
    console.setLayout(new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %-5p %c:%L - %m%n"))
    console.activateOptions()

    val file = new RollingFileAppender()
    file.setFile(Locations.PrimaryLog)
    file.setMaximumFileSize(1024 * 1024 * 10)
    file.setMaxBackupIndex(10)
    file.setLayout(new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %-5p %c:%L - %m%n"))
    file.setAppend(true)
    file.activateOptions()

    Logger.getRootLogger.getLoggerRepository.resetConfiguration()
    Logger.getRootLogger.setLevel(Level.DEBUG)
    Logger.getRootLogger.addAppender(file)
    Logger.getRootLogger.addAppender(console)
    Logger.getLogger("spark.route").setLevel(Level.ERROR)
    Logger.getLogger("org.eclipse.jetty").setLevel(Level.ERROR)
    Logger.getLogger("io.netty").setLevel(Level.ERROR)
    Logger.getLogger("org.littleshoot.proxy").setLevel(Level.ERROR)
    Logger.getLogger("org.apache.http").setLevel(Level.ERROR)
  }

}
