package monika.server.script

import monika.server.UseScalaz
import monika.server.script.library._
import monika.server.script.property.Property
import org.reflections.Reflections
import shapeless.Typeable

import scala.collection.JavaConverters._
import scala.language.implicitConversions

abstract class Script(val props: Property*) extends UseScalaz
  with ReaderOps
  with RestrictionOps
  with QueueOps
  with InputOps
  with RandomOps {

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
  private val reflections = new Reflections("monika.server.script")
  private def findAllObjects[T](cl: Class[T])(implicit t: Typeable[T]): Vector[T] = {
    reflections.getSubTypesOf(cl).asScala.toVector.map(cl => cl.getField("MODULE$").get(null).asInstanceOf[T])
  }
  val allScripts: Vector[Script] = findAllObjects(classOf[Script])
  val allScriptsByName: Map[String, Script] = allScripts.map(s => s.name -> s).toMap
}
