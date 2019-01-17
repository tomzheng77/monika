package monika.orbit

import java.io.File

import monika.server.Constants.Locations
import org.apache.log4j._
import org.bitcoinj.core.{ECKey, NetworkParameters}
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.TestNet3Params

object Orbit {

  // rebit <address>: pockets bitcoin from the specified address
  // orbit <time>: at <time>, you will have 10 minutes to solve a captcha, otherwise all bitcoin will be lost
  // withdraw <address> withdraws all bitcoin to the given address

  def main(args: Array[String]): Unit = {
    logToConsole()
    val params: NetworkParameters = TestNet3Params.get()
    val filePrefix = "forwarding-service-testnet"
    val kit = new WalletAppKit(params, new File("."), filePrefix) {
      override def onSetupCompleted(): Unit = {
        if (wallet().getKeyChainGroupSize() < 1)
          wallet().importKey(new ECKey())
      }
    }
    kit.startAsync()
    kit.awaitRunning()
  }

  private def logToConsole(): Unit = {
    // https://www.mkyong.com/logging/log4j-log4j-properties-examples/
    // https://stackoverflow.com/questions/8965946/configuring-log4j-loggers-programmatically
    val console = new ConsoleAppender()
    console.setLayout(new PatternLayout("%d{yyyy-MM-dd HH:mm:ss} %-5p %c:%L - %m%n"))
    console.activateOptions()
    Logger.getRootLogger.getLoggerRepository.resetConfiguration()
    Logger.getRootLogger.setLevel(Level.DEBUG)
    Logger.getRootLogger.addAppender(console)
  }

}
