package monika.server

import java.util.{Timer, TimerTask}

import monika.server.Subprocess.{LOGGER, _}
import LittleProxy.writeCertificatesToFiles
import org.apache.log4j._
import org.slf4j.LoggerFactory
import scalaz.Tag

object Bootstrap {

  private val LOGGER = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    setupLogger()
    if (System.getenv("USER") != "root") {
      LOGGER.warn("user is not root")
    }
    LOGGER.info("M.O.N.I.K.A starting...")
    checkIfProgramsAreExecutable()
    writeCertificatesToFiles()
    rejectOutgoingHttp(forUser = Constants.MonikaUser)
    LOGGER.info("M.O.N.I.K.A started")
  }

  private def checkIfProgramsAreExecutable(): Unit = {
    val programs = Constants.ProfilePrograms ++ Constants.CallablePrograms.asList
    val cannotExecute = programs.filter(findProgramLocation(_).isEmpty)
    for (program <- cannotExecute) {
      val programName = Tag.unwrap(program)
      LOGGER.warn(s"cannot find executable program: $programName")
    }
  }

  private def rejectOutgoingHttp(forUser: String): Unit = {
    call("iptables", s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 80 -j REJECT".split(' '))
    call("iptables", s"-w 10 -A OUTPUT -p tcp -m owner --uid-owner $forUser --dport 443 -j REJECT".split(' '))
  }

  private def setupLogger(): Unit = {
    // https://www.mkyong.com/logging/log4j-log4j-properties-examples/
    // https://stackoverflow.com/questions/8965946/configuring-log4j-loggers-programmatically
    val console = new ConsoleAppender()
    console.setLayout(new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %-5p %c:%L - %m%n"))
    console.activateOptions()

    val file = new RollingFileAppender()
    file.setFile(Constants.Locations.PrimaryLog)
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
  }

}
