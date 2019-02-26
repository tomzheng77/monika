package monika.server.script

object RestartProxy extends Script  {
  override def run(args: Vector[String]): IOS[Unit] = {
    getState().flatMap(s ⇒ restartProxy(s.filter))
  }
}
