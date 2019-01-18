package monika.server.persist

import java.io.{ByteArrayOutputStream, OutputStreamWriter, StringWriter}

import monika.server.Structs.MonikaState
import monika.server.UseJSON
import org.scalatest.{FlatSpec, Matchers}

class JSONTests extends FlatSpec with Matchers with UseJSON {

  "JSON" should "succeed" in {
    val state = MonikaState()
    val writer = new StringWriter()
    writeItemAsJSON(state, writer)
    writer.toString.nonEmpty should be(true)
  }

  "JSON" should "fail" in {
    val state = MonikaState()
    val output = new ByteArrayOutputStream()
    val writer = new OutputStreamWriter(output)
    writeItemAsJSON(state, writer)
    output.toByteArray.length should be(0)
  }

}
