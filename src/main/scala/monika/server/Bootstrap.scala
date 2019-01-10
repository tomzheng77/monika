package monika.server

import java.util.{Timer, TimerTask}

import monika.server.Subprocess._
import LittleProxy.writeCertificatesToFiles
import org.apache.log4j._
import org.slf4j.LoggerFactory

object Bootstrap {

  private val LOGGER = LoggerFactory.getLogger(getClass)

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

  def setInterval(fn: () => Unit, ms: Int): Unit = {
    val timer = new Timer()
    timer.schedule(new TimerTask {
      override def run(): Unit = fn()
    }, 0, ms)
  }

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

}
