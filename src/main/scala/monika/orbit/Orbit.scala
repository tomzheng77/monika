package monika.orbit

import java.io.File

import org.apache.log4j._
import org.bitcoinj.core._
import org.bitcoinj.kits.WalletAppKit
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener

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
    println("receive address: " + kit.wallet().currentReceiveAddress())
    kit.wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener {
      override def onCoinsReceived(wallet: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin): Unit = {
        val coin = tx.getValueSentToMe(wallet)
        System.out.println("Received tx for " + coin.toFriendlyString + ": " + tx)
        System.out.println("Transaction will be forwarded after it confirms.")
      }
    })

    val value = kit.wallet().getBalance
    System.out.println("Forwarding " + value.toFriendlyString + " BTC")
    // Now send the coins back! Send with a small fee attached to ensure rapid confirmation.
    val amountToSend = value.subtract(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE)
    val forwardingAddress = Address.fromBase58(params, "")
    val sendResult = kit.wallet.sendCoins(kit.peerGroup, forwardingAddress, amountToSend)
    System.out.println("Sending ...")
    // Register a callback that is invoked when the transaction has propagated across the network.
    // This shows a second style of registering ListenableFuture callbacks, it works when you don't
    // need access to the object the future returns.

    while (true) {
      Thread.sleep(1000)
    }
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
    Logger.getLogger("org.bitcoinj.wallet").setLevel(Level.INFO)
    Logger.getLogger("org.bitcoinj.core").setLevel(Level.ERROR)
    Logger.getLogger("org.bitcoinj.net").setLevel(Level.ERROR)
  }

}
