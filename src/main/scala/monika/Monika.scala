package monika

import monika.client.SignalClient
import monika.orbit.OrbitServer
import monika.server.MonikaServer

object Monika {

  def main(args: Array[String]): Unit = {
    args.headOption match {
      case None => SignalClient.main(args)
      case Some("--server") => MonikaServer.main(args)
      case Some("--orbit") => OrbitServer.main(args)
      case Some(other) => println(s"unknown mode $other")
    }
  }

}
