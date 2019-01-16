package monika

import monika.client.SignalClient
import monika.server.Bootstrap

object Monika {

  def main(args: Array[String]): Unit = {
    if (args.contains("--server")) Bootstrap.main(args)
    else SignalClient.main(args)
  }

}
