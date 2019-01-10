package monika

import monika.client.SimpleHttpClient
import monika.server.Bootstrap

object Monika {

  def main(args: Array[String]): Unit = {
    if (args.contains("--server")) Bootstrap.main(args)
    else SimpleHttpClient.main(args)
  }

}
