package monika.server.subprocess

import monika.Primitives.Filename
import scalaz.@@

object Commands {

  sealed trait Command extends Product {
    val name: String @@ Filename = Filename(productPrefix)
  }
  case object id extends Command
  case object groups extends Command
  case object passwd extends Command
  case object chmod extends Command
  case object chown extends Command
  case object iptables extends Command
  case object usermod extends Command
  case object groupadd extends Command
  case object killall extends Command

  val CommandArray: IndexedSeq[Command] = Array(id, groups, passwd, chmod, chown, iptables, usermod, groupadd, killall)

}
