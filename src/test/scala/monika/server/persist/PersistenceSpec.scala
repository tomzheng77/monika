package monika.server.persist

import java.time.{LocalDate, LocalDateTime}

import monika.Primitives._
import monika.server.LittleProxy.ProxySettings
import monika.server.Persistence
import monika.server.Structs._
import org.scalacheck.Prop.forAll
import org.scalacheck.{Gen, Properties}

import scala.util.Try

object PersistenceSpec extends Properties("Persistence") {

  private def randomProxySettings: Gen[ProxySettings] = for {
    transparent <- Gen.oneOf(true, false)
    allow <- Gen.listOf(Gen.asciiPrintableStr).map(_.toVector)
    reject <- Gen.listOf(Gen.asciiPrintableStr).map(_.toVector)
  } yield ProxySettings(transparent, allow, reject)

  private def randomBookmark: Gen[Bookmark] = for {
    name <- Gen.alphaNumStr
    url <- Gen.asciiPrintableStr
  } yield Bookmark(name, url)

  private def randomProfile: Gen[Profile] = for {
    name <- Gen.alphaNumStr
    programs <- Gen.listOf(Gen.alphaNumStr.map(FileName)).map(_.toVector)
    projects <- Gen.listOf(Gen.alphaNumStr.map(FileName)).map(_.toVector)
    bookmarks <- Gen.listOf(randomBookmark).map(_.toVector)
    proxy <- randomProxySettings
  } yield Profile(name, programs, projects, bookmarks, proxy)

  private def NowDateTime: LocalDateTime = LocalDateTime.now()

  private def sequence[T](list: Vector[Gen[T]]): Gen[Vector[T]] = {
    Gen.sequence[Vector[T], T](list)
  }

  private def randomQueue: Gen[Vector[(LocalDateTime, Action)]] = {
    import scalaz.syntax.id._
    Gen.choose(0, 10).flatMap(i => {
      Gen.listOfN(i, Gen.choose(1, 100)).flatMap(l => {
        l.foldLeft((NowDateTime, Vector[Gen[(LocalDateTime, Action)]]()))((pair, t) => {
          val start = pair._1
          val items = pair._2
          val end = start.plusMinutes(t)
          (end, items :+ randomProfile.map(p => start -> SetProfile(p)))
        })._2 |> sequence
      })
    })
  }

  private def randomState: Gen[MonikaState] = for {
    queue <- randomQueue
    proxy <- randomProxySettings
  } yield MonikaState(queue, proxy)

  property("serialize") = {
    forAll(randomState)(a => {
      import org.json4s.native.JsonMethods._
      import scalaz.syntax.id._
      val json = Persistence.stateToJson(a)
      Try(Persistence.jsonToState(json)).failed.foreach(ex => {
        json |> render |> pretty |> println
        ex.printStackTrace()
      })
      Persistence.jsonToState(Persistence.stateToJson(a)) == a
    })
  }

/*
  Corrugated Iron
  Plywood 2400 x 1200 (x5)
  Stick 2400 (x4)
  Screws
  Electric Drill
  Large Ruler
  Exterior Paint
 */

}
