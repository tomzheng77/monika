package monika.server.script

object RewriteCerts extends Script with RequireRoot {

  override def run(args: Vector[String]): SC[Unit] = SC(api => api.rewriteCertificates())

}
