package monika.server.script.internal

import monika.server.script.Script
import monika.server.script.library.RestrictionOps
import monika.server.script.property.Internal

object ForceOut extends Script(Internal) with RestrictionOps {

  override def run(args: Vector[String]): SC[Unit] = forceLogout()

}
