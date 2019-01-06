package monika.server.persist

import java.time.{LocalDate, LocalDateTime}

import monika.server.pure.Model.{Bookmark, MonikaState, Profile, ProfileRequest}
import org.scalacheck.{Arbitrary, Gen, Properties}
import org.scalacheck.Prop.forAll
import monika.Primitives._
import monika.server.proxy.ProxyServer.ProxySettings

object StateStoreTest extends Properties("StateStore") {

  val randomProxySettings: Gen[ProxySettings] = for {
    transparent <- Gen.oneOf(true, false)
    allow <- Gen.listOf(Gen.asciiPrintableStr).map(_.toVector)
    reject <- Gen.listOf(Gen.asciiPrintableStr).map(_.toVector)
  } yield ProxySettings(transparent, allow, reject)

  val randomBookmark: Gen[Bookmark] = for {
    name <- Gen.alphaNumStr
    url <- Gen.asciiPrintableStr
  } yield Bookmark(name, url)

  val randomProfile: Gen[Profile] = for {
    name <- Gen.alphaNumStr
    programs <- Gen.listOf(Gen.alphaNumStr.map(FileName)).map(_.toVector)
    projects <- Gen.listOf(Gen.alphaNumStr.map(FileName)).map(_.toVector)
    bookmarks <- Gen.listOf(randomBookmark).map(_.toVector)
    proxy <- randomProxySettings
  } yield Profile(name, programs, projects, bookmarks, proxy)

  val randomDateTime: Gen[LocalDateTime] = for {
    sinceNow <- Gen.choose(-1000, 1000)
  } yield LocalDateTime.now().plusMinutes(sinceNow)

  val randomProfileRequest: Gen[ProfileRequest] = for {
    start <- randomDateTime
    end <- randomDateTime
    profile <- randomProfile
  } yield ProfileRequest(start, end, profile)

  def randomPair[A, B](genA: Gen[A], genB: Gen[B]): Gen[(A, B)] = for {
    a <- genA
    b <- genB
  } yield (a, b)

  val randomDate: Gen[LocalDate] = for {
    sinceNow <- Gen.choose(-100, 100)
  } yield LocalDate.now().plusDays(sinceNow)

  val randomState: Gen[MonikaState] = for {
    queue <- Gen.listOf(randomProfileRequest).map(_.toVector)
    active <- Gen.option(randomProfileRequest)
    knownProfiles <- Gen.mapOf(randomPair(Gen.alphaNumStr, randomProfile))
    passwords <- Gen.mapOf(randomPair(randomDate, Gen.alphaNumStr))
  } yield MonikaState(queue, active, knownProfiles, passwords)

  implicit val arbitrary: Arbitrary[MonikaState] = Arbitrary(randomState)
  property("serialize") = forAll { a: MonikaState =>
    StateStore.jsonToState(StateStore.stateToJson(a)) == a
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
