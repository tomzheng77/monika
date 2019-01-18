package monika.server.script.library

import monika.server.UseScalaz

trait ReaderOps extends UseScalaz {

  type ScriptAPI = monika.server.script.ScriptAPI
  type SC[A] = Reader[ScriptAPI, A]
  protected implicit def SC[A](fn: ScriptAPI => A): SC[A] = Reader(fn)

  def printLine(text: String): SC[Unit] = SC(api => api.println(text))

}
