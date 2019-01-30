package monika.server.persist

import java.io.StringWriter
import java.time.{LocalDateTime, ZoneOffset}

import monika.server.Structs._
import monika.server.UseJSON
import monika.server.proxy.{Filter, TransparentFilter, URLFilter}
import monika.server.script.{Request, Script}
import monika.server.script.internal.Unlock
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop.forAll
import org.scalacheck.{Arbitrary, Gen, Properties}

object JSONSpec extends Properties("JSON") with UseJSON {

  implicit val LocalDateTimeArb: Arbitrary[LocalDateTime] = Arbitrary({
    val nowTime = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)
    for {
      timeSinceEpoch <- Gen.choose(0, nowTime)
    } yield LocalDateTime.ofEpochSecond(timeSinceEpoch, 0, ZoneOffset.UTC)
  })

  implicit val ScriptArb: Arbitrary[Script] = Arbitrary(Gen.oneOf(Script.allScripts))
  implicit val FutureActionArb: Arbitrary[FutureAction] = Arbitrary(Gen.resultOf(FutureAction))
  implicit def VectorGenArb[A: Arbitrary]: Arbitrary[Vector[A]] = Arbitrary(Gen.listOf(arbitrary[A]).map(l => l.toVector))
  implicit val FilterGenArb: Arbitrary[Filter] = Arbitrary(Gen.oneOf(
    Gen.const(TransparentFilter),
    Gen.resultOf(URLFilter)
  ))
  implicit val MonikaGenArb: Arbitrary[MonikaState] = Arbitrary(Gen.resultOf(MonikaState))

  property("serialize string lists") = {
    forAll(arbitrary[List[String]])(a => {
      val writer = new StringWriter()
      writeItemAsJSON(a, writer)
      readJSONToItem[List[String]](writer.toString) == a
    })
  }

  property("serialize date time") = {
    forAll(arbitrary[LocalDateTime])(a => {
      val writer = new StringWriter()
      writeItemAsJSON(a, writer)
      readJSONToItem[LocalDateTime](writer.toString) == a
    })
  }

  property("serialize future action") = {
    forAll(arbitrary[FutureAction])(a => {
      val writer = new StringWriter()
      writeItemAsJSON(a, writer)
      readJSONToItem[FutureAction](writer.toString) == a
    })
  }

  property("serialize filter") = {
    forAll(arbitrary[Filter])(a => {
      val writer = new StringWriter()
      writeItemAsJSON(a, writer)
      readJSONToItem[Filter](writer.toString) == a
    })
  }

  property("serialize") = {
    forAll(arbitrary[MonikaState])(a => {
      val writer = new StringWriter()
      writeItemAsJSON(a, writer)
      readJSONToItem[MonikaState](writer.toString) == a
    })
  }

}
