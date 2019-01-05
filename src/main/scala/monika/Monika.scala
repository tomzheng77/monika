package monika

import monika.client.SimpleHttpClient
import monika.server.MonikaServer

object Monika {

  def main(args: Array[String]): Unit = {
    if (args.contains("--server")) MonikaServer.main(args)
    else SimpleHttpClient.main(args)
  }

}
