package monika.server.persist

import java.io.StringWriter
import java.time.{LocalDate, LocalDateTime, ZoneOffset}

import monika.server.Structs._
import monika.server.UseJSON
import monika.server.proxy.{Filter, TransparentFilter, URLFilter}
import monika.server.script.Script
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

  implicit val LocalDateArb: Arbitrary[LocalDate] = Arbitrary(arbitrary[LocalDateTime].map(_.toLocalDate))
  implicit val ScriptArb: Arbitrary[Script] = Arbitrary(Gen.oneOf(Script.allScripts))
  implicit val FutureActionArb: Arbitrary[FutureAction] = Arbitrary(Gen.resultOf(FutureAction))
  implicit def VectorGenArb[A: Arbitrary]: Arbitrary[Vector[A]] = Arbitrary(Gen.listOf(arbitrary[A]).map(l => l.toVector))
  implicit def MapGenArb[K: Arbitrary, V: Arbitrary]: Arbitrary[Map[K, V]] = Arbitrary(
    Gen.mapOf(for {
      k ← arbitrary[K]
      v ← arbitrary[V]
    } yield k → v)
  )
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
