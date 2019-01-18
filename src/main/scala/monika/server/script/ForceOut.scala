package monika.server.script

import monika.server.script.library.RestrictionOps

object ForceOut extends Script with RequireRoot with RestrictionOps {

  override def run(args: Vector[String]): SC[Unit] = forceLogout()

}
