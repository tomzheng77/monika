package monika.server.script

import monika.server.Structs.{Action, MonikaState}
import monika.server.UseDateTime
import monika.server.script.internal.{Brick, Freedom, LockProfile, Unlock}
import org.scalatest.{FlatSpec, Matchers}

class RequestBetweenSpec extends FlatSpec with Matchers with UseDateTime {

  "RequestBetween" should "handle being contained in freedom" in {
    val state = MonikaState(queue = Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
    val newState = RequestBetween.requestBetweenInternal(
      parseDateTime("2019-03-04 20:10:00").get,
      parseDateTime("2019-03-04 20:20:00").get
    )(Brick, Vector())(state)

    newState.queue should be(Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 20:10:00").get, Brick),
      Action(parseDateTime("2019-03-04 20:20:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
  }

  it should "handle overlaps with freedom" in {
    val state = MonikaState(queue = Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
    val newState = RequestBetween.requestBetweenInternal(
      parseDateTime("2019-03-04 20:30:00").get,
      parseDateTime("2019-03-04 21:20:00").get
    )(Brick, Vector())(state)

    newState.queue should be(Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 20:30:00").get, Brick),
      Action(parseDateTime("2019-03-04 21:20:00").get, Unlock)
    ))
  }

  it should "pass case 3" in {
    val state = MonikaState(queue = Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
    val newState = RequestBetween.requestBetweenInternal(
      parseDateTime("2019-03-04 18:30:00").get,
      parseDateTime("2019-03-04 19:20:00").get
    )(Brick, Vector())(state)

    newState.queue should be(Vector(
      Action(parseDateTime("2019-03-04 18:30:00").get, Brick),
      Action(parseDateTime("2019-03-04 19:20:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
  }

  it should "pass case 4" in {
    val state = MonikaState(queue = Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
    val newState = RequestBetween.requestBetweenInternal(
      parseDateTime("2019-03-04 18:30:00").get,
      parseDateTime("2019-03-04 19:20:00").get
    )(Brick, Vector())(state)

    newState.queue should be(Vector(
      Action(parseDateTime("2019-03-04 18:30:00").get, Brick),
      Action(parseDateTime("2019-03-04 19:20:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
  }

  it should "handle being after unlock" in {
    val state = MonikaState(queue = Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
    val newState = RequestBetween.requestBetweenInternal(
      parseDateTime("2019-03-04 22:30:00").get,
      parseDateTime("2019-03-04 23:20:00").get
    )(LockProfile, Vector("", "", "google-chrome"))(state)

    newState.queue should be(Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 22:30:00").get, LockProfile, Vector("", "", "google-chrome")),
      Action(parseDateTime("2019-03-04 23:20:00").get, Unlock)
    ))
  }

  it should "handle overlaps with end" in {
    val state = MonikaState(queue = Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
    val newState = RequestBetween.requestBetweenInternal(
      parseDateTime("2019-03-04 21:00:00").get,
      parseDateTime("2019-03-04 23:20:00").get
    )(LockProfile, Vector("", "", "google-chrome"))(state)

    newState.queue should be(Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, LockProfile, Vector("", "", "google-chrome")),
      Action(parseDateTime("2019-03-04 23:20:00").get, Unlock)
    ))
  }

  "removeDuplicate" should "remove the second script if the one before is the same" in {
    val out = RequestBetween.removeDuplicate(List(
      Action(parseDateTime("2019-03-04 18:30:00").get, Brick),
      Action(parseDateTime("2019-03-04 19:20:00").get, Freedom),
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))

    out should be(List(
      Action(parseDateTime("2019-03-04 18:30:00").get, Brick),
      Action(parseDateTime("2019-03-04 19:20:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
  }

  it should "remove the third script or more" in {
    val out = RequestBetween.removeDuplicate(List(
      Action(parseDateTime("2019-03-04 18:30:00").get, Brick),
      Action(parseDateTime("2019-03-04 19:20:00").get, LockProfile, Vector("", "", "google-chrome")),
      Action(parseDateTime("2019-03-04 19:30:00").get, LockProfile, Vector("", "", "google-chrome")),
      Action(parseDateTime("2019-03-04 19:40:00").get, LockProfile, Vector("", "", "google-chrome")),
      Action(parseDateTime("2019-03-04 19:50:00").get, LockProfile, Vector("", "", "google-chrome")),
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 20:30:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))

    out should be(List(
      Action(parseDateTime("2019-03-04 18:30:00").get, Brick),
      Action(parseDateTime("2019-03-04 19:20:00").get, LockProfile, Vector("", "", "google-chrome")),
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
  }

}
