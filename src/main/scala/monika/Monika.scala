package monika

import monika.`super`.Super
import monika.client.SignalClient
import monika.server.Bootstrap

object Monika {

  def main(args: Array[String]): Unit = {
    args.headOption match {
      case None => SignalClient.main(args)
      case Some("--server") => Bootstrap.main(args)
      case Some("--super") => Super.main(args.drop(1))
    }
  }

}
