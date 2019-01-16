package monika.server

import java.io.Writer

import org.json4s.native.{Document, JsonMethods, Printer}
import org.json4s.{DefaultFormats, DoubleMode, Formats, JsonDSL}

trait UseJSON extends JsonDSL with DoubleMode {

  implicit val formats: Formats = DefaultFormats
  type JValue = org.json4s.JValue
  type JsonInput = org.json4s.JsonInput
  def parseJSON(input: JsonInput): JValue = JsonMethods.parse(input)
  def renderJSON(json: JValue): Document = JsonMethods.render(json)
  def printCompact[A <: Writer](d: Document, out: A): A = Printer.compact(d, out)

}
