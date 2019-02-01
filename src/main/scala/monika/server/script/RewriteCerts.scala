package monika.server.script

import monika.server.script.property.RootOnly

object RewriteCerts extends Script(RootOnly) {

  override def run(args: Vector[String]): IOS[Unit] = IOS(api => api.rewriteCertificates())

}
