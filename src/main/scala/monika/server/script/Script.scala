package monika.server.script

import monika.server.UseScalaz
import monika.server.script.internal._
import monika.server.script.library.ReaderOps

import scala.language.implicitConversions

trait Script extends UseScalaz
  with ReaderOps {

  val name: String = {
    val className = getClass.getSimpleName
    className.flatMap {
      case '$' => ""
      case c if Character.isUpperCase(c) => "-" + c.toLower
      case other => other.toString
    } |> (s => s.dropWhile(_ == '-'))
  }

  def run(args: Vector[String]): SC[Unit]

}

object Script extends UseScalaz {
  val allScripts = Vector(Brick, DelayUnlock, ForceOut, LockSite, SetProfile, Status, Unlock, RewriteCerts)
  val allScriptsByName: Map[String, Script] = allScripts.map(s => s.name -> s).toMap
}
