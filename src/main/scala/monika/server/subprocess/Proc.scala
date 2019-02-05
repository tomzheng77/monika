package monika.server.subprocess

import monika.Primitives.CanonicalPath
import scalaz.@@

case class Proc(pid: Int, exe: String @@ CanonicalPath)
