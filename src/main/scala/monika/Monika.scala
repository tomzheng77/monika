package monika

import monika.adm.Admin
import monika.client.SignalClient
import monika.server.Bootstrap

object Monika {

  def main(args: Array[String]): Unit = {
    args.headOption match {
      case None => SignalClient.main(args)
      case Some("--server") => Bootstrap.main(args)
      case Some("--admin") => Admin.main(args.drop(1))
      case Some(other) => println(s"unknown mode $other")
    }
  }

}
