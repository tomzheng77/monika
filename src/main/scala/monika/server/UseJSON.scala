package monika.server

import java.io.Writer
import java.time.LocalDateTime

import monika.server.proxy.{URLFilter, TransparentFilter}
import monika.server.script.Script
import org.json4s.JsonAST.JString
import org.json4s.jackson.JsonMethods
import org.json4s.native.Serialization
import org.json4s.{CustomSerializer, DefaultFormats, DoubleMode, Formats, JsonDSL, ShortTypeHints}

trait UseJSON extends JsonDSL with DoubleMode with UseDateTime {

  private implicit val defaultFormats: Formats = DefaultFormats
  private type JValue = org.json4s.JValue
  protected type JsonInput = org.json4s.JsonInput

  private val classes: List[Class[_]] = List(
    classOf[URLFilter],
    TransparentFilter.getClass,
  ) ++ Script.allScripts.map(_.getClass)

  object ScriptSerializer extends CustomSerializer[Script](_ => (
    { case JString(name) => Script.allScriptsByName(name) },
    { case s: Script => JString(s.name) }
  ))

  object LocalDateTimeSerializer extends CustomSerializer[LocalDateTime](_ => (
    { case JString(str) => LocalDateTime.parse(str, DefaultFormatter) },
    { case s: LocalDateTime => JString(s.format(DefaultFormatter)) }
  ))

  private val serializeFormats: Formats = Serialization.formats(ShortTypeHints(classes)) + ScriptSerializer + LocalDateTimeSerializer
  protected def writeItemAsJSON[W <: Writer](item: Any, out: W): W = {
    Serialization.writePretty(item, out)(serializeFormats)
  }

  protected def readJSONToItem[A: Manifest](input: JsonInput): A = {
    val in = JsonMethods.parse(input)
    in.extract[A](serializeFormats, manifest[A])
  }

}
