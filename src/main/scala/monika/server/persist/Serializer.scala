package monika.server.persist

import monika.server.pure.Model.MonikaState
import org.json4s.JsonAST.JValue

trait Serializer {

  def serialize(state: MonikaState): JValue
  def deserialize(obj: JValue): MonikaState

}
