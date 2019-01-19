package monika.server.script

import monika.server.UseScalaz
import monika.server.script.internal._
import monika.server.script.library.{ReaderOps, RestrictionOps}
import monika.server.script.property.Property

import scala.language.implicitConversions

abstract class Script(props: Property*) extends UseScalaz
  with ReaderOps
  with RestrictionOps {

  def hasProperty(property: Property): Boolean = {
    props.contains(property)
  }

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
