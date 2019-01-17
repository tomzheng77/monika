package monika.server.script

import java.io.PrintWriter

import monika.server.UseScalaz

import scala.language.implicitConversions

trait Script extends UseScalaz {

  val name: String = {
    val className = getClass.getSimpleName
    className.flatMap {
      case '$' => ""
      case c if Character.isUpperCase(c) => "-" + c.toLower
      case other => other.toString
    } |> (s => s.dropWhile(_ == '-'))
  }

  def run(args: Vector[String], out: PrintWriter): Unit

}

object Script {
  val allScripts = Vector(Brick, SetProfile, Status, Unlock)
  val allScriptsByKey: Map[String, Script] = allScripts.map(s => s.name -> s).toMap
}
