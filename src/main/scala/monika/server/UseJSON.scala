package monika.server

import java.io.Writer
import java.time.{LocalDate, LocalDateTime}

import monika.server.proxy.{TransparentFilter, URLFilter}
import monika.server.script.Script
import org.json4s.JsonAST.JString
import org.json4s.jackson.JsonMethods
import org.json4s.native.Serialization
import org.json4s.{CustomKeySerializer, CustomSerializer, DefaultFormats, DoubleMode, Formats, JsonDSL, ShortTypeHints}

trait UseJSON extends JsonDSL with DoubleMode with UseDateTime {

  private implicit val defaultFormats: Formats = DefaultFormats
  private type JValue = org.json4s.JValue
  protected type JsonInput = org.json4s.JsonInput

  private val classes: List[Class[_]] = List(
    classOf[URLFilter],
    TransparentFilter.getClass,
  ) ++ Script.allScripts.map(_.getClass)

  private object ScriptSerializer extends CustomSerializer[Script](_ => (
    { case JString(name) => Script.allScriptsByName(name) },
    { case s: Script => JString(s.name) }
  ))

  private object LocalDateTimeSerializer extends CustomSerializer[LocalDateTime](_ => (
    { case JString(str) => parseDateTime(str).getOrThrow() },
    { case s: LocalDateTime => JString(s.format()) }
  ))

  private object LocalDateSerializer extends CustomKeySerializer[LocalDate](_ => (
    { case str => LocalDate.parse(str) },
    { case s: LocalDate => s.toString }
  ))

  private val serializeFormats: Formats = Serialization.formats(ShortTypeHints(classes)) +
    ScriptSerializer +
    LocalDateTimeSerializer +
    LocalDateSerializer

  protected def writeItemAsJSON[W <: Writer](item: Any, out: W): W = {
    Serialization.writePretty(item, out)(serializeFormats)
  }

  protected def readJSONToItem[A: Manifest](input: JsonInput): A = {
    val in = JsonMethods.parse(input)
    in.extract[A](serializeFormats, manifest[A])
  }

}
