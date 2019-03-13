package monika.server.script

import monika.server.Structs.{Action, MonikaState}
import monika.server.UseDateTime
import monika.server.script.internal.{Brick, Freedom, LockProfile, Unlock}
import org.scalatest.{FlatSpec, Matchers}

class RequestSpec extends FlatSpec with Matchers with UseDateTime {

  "RequestBetween" should "handle being contained in freedom" in {
    val state = MonikaState(queue = Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
    val newState = Request.requestBetweenInternal(
      parseDateTime("2019-03-04 20:10:00").get,
      parseDateTime("2019-03-04 20:20:00").get
    )(Brick, Vector())(state).getOrElse(state)

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
    val newState = Request.requestBetweenInternal(
      parseDateTime("2019-03-04 20:30:00").get,
      parseDateTime("2019-03-04 21:20:00").get
    )(Brick, Vector())(state).getOrElse(state)

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
    val newState = Request.requestBetweenInternal(
      parseDateTime("2019-03-04 18:30:00").get,
      parseDateTime("2019-03-04 19:20:00").get
    )(Brick, Vector())(state).getOrElse(state)

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
    val newState = Request.requestBetweenInternal(
      parseDateTime("2019-03-04 18:30:00").get,
      parseDateTime("2019-03-04 19:20:00").get
    )(Brick, Vector())(state).getOrElse(state)

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
    val newState = Request.requestBetweenInternal(
      parseDateTime("2019-03-04 22:30:00").get,
      parseDateTime("2019-03-04 23:20:00").get
    )(LockProfile, Vector("", "", "google-chrome"))(state).getOrElse(state)

    newState.queue should be(Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 22:30:00").get, LockProfile, Vector("", "", "google-chrome")),
      Action(parseDateTime("2019-03-04 23:20:00").get, Unlock)
    ))
  }

  it should "reject if last action is non-free" in {
    val state = MonikaState(
      queue = Vector(
        Action(parseDateTime("2019-03-04 20:00:00").get, LockProfile, Vector("", "", "idea")),
        Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
      ))
    val newState = Request.requestBetweenInternal(
      parseDateTime("2019-03-04 20:30:00").get,
      parseDateTime("2019-03-04 21:20:00").get
    )(LockProfile, Vector("", "", "google-chrome"))(state).getOrElse(state)

    newState.queue should be(Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, LockProfile, Vector("", "", "idea")),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
  }

  it should "reject if previous action is non-free" in {
    val state = MonikaState(
      previous = Some(Action(parseDateTime("2019-03-04 20:00:00").get, LockProfile, Vector("", "", "idea"))),
      queue = Vector(
        Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
      ))
    val newState = Request.requestBetweenInternal(
      parseDateTime("2019-03-04 20:30:00").get,
      parseDateTime("2019-03-04 21:20:00").get
    )(LockProfile, Vector("", "", "google-chrome"))(state).getOrElse(state)

    newState.queue should be(Vector(
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
  }

  it should "handle overlaps with end" in {
    val state = MonikaState(queue = Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, Unlock)
    ))
    val newState = Request.requestBetweenInternal(
      parseDateTime("2019-03-04 21:00:00").get,
      parseDateTime("2019-03-04 23:20:00").get
    )(LockProfile, Vector("", "", "google-chrome"))(state).getOrElse(state)

    newState.queue should be(Vector(
      Action(parseDateTime("2019-03-04 20:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 21:00:00").get, LockProfile, Vector("", "", "google-chrome")),
      Action(parseDateTime("2019-03-04 23:20:00").get, Unlock)
    ))
  }

  it should "handle this complex case" in {
    var state = MonikaState(queue = Vector(
      Action(parseDateTime("2019-03-04 01:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 02:00:00").get, LockProfile, Vector("", "", "idea")),
      Action(parseDateTime("2019-03-04 03:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 04:00:00").get, LockProfile, Vector("", "", "idea,monika")),
      Action(parseDateTime("2019-03-04 05:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 06:00:00").get, Brick),
      Action(parseDateTime("2019-03-04 07:00:00").get, Unlock)
    ))

    state = Request.requestBetweenInternal(
      parseDateTime("2019-03-04 01:30:00").get,
      parseDateTime("2019-03-04 04:30:00").get
    )(LockProfile, Vector("", "", "google-chrome"))(state).getOrElse(state)

    state = Request.requestBetweenInternal(
      parseDateTime("2019-03-04 03:20:00").get,
      parseDateTime("2019-03-04 03:40:00").get
    )(LockProfile, Vector("", "", "google-chrome"))(state).getOrElse(state)

    state.queue should be(Vector(
      Action(parseDateTime("2019-03-04 01:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 02:00:00").get, LockProfile, Vector("", "", "idea")),
      Action(parseDateTime("2019-03-04 03:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 03:20:00").get, LockProfile, Vector("", "", "google-chrome")),
      Action(parseDateTime("2019-03-04 03:40:00").get, Freedom),
      Action(parseDateTime("2019-03-04 04:00:00").get, LockProfile, Vector("", "", "idea,monika")),
      Action(parseDateTime("2019-03-04 05:00:00").get, Freedom),
      Action(parseDateTime("2019-03-04 06:00:00").get, Brick),
      Action(parseDateTime("2019-03-04 07:00:00").get, Unlock)
    ))
  }

  it should "handle this iterative construction" in {
    var state = MonikaState()
    state = Request.requestBetweenInternal(
      parseDateTime("2019-03-05 22:00:00").get,
      parseDateTime("2019-03-05 23:30:00").get
    )(Brick, Vector())(state).getOrElse(state)
    state = Request.requestBetweenInternal(
      parseDateTime("2019-03-05 10:00:00").get,
      parseDateTime("2019-03-05 11:30:00").get
    )(LockProfile, Vector("A", "B", "C"))(state).getOrElse(state)
    state = Request.requestBetweenInternal(
      parseDateTime("2019-03-05 11:30:00").get,
      parseDateTime("2019-03-05 12:00:00").get
    )(Brick, Vector())(state).getOrElse(state)

    state.queue should be(Vector(
      Action(parseDateTime("2019-03-05 10:00:00").get, LockProfile, Vector("A", "B", "C")),
      Action(parseDateTime("2019-03-05 11:30:00").get, Brick),
      Action(parseDateTime("2019-03-05 12:00:00").get, Freedom),
      Action(parseDateTime("2019-03-05 22:00:00").get, Brick),
      Action(parseDateTime("2019-03-05 23:30:00").get, Unlock)
    ))
  }

  it should "remove from start if same as previous" in {
    var state = MonikaState(
      previous = Some(Action(parseDateTime("2019-03-05 08:00:00").get, Freedom, Vector("D", "E", "F"))),
      queue = Vector(
        Action(parseDateTime("2019-03-05 12:00:00").get, Unlock)
      )
    )
    state = Request.requestBetweenInternal(
      parseDateTime("2019-03-05 10:00:00").get,
      parseDateTime("2019-03-05 14:00:00").get
    )(Freedom, Vector("D", "E", "F"))(state).getOrElse(state)

    state.queue should be(Vector(
      Action(parseDateTime("2019-03-05 14:00:00").get, Unlock)
    ))
  }

  "removeDuplicate" should "remove the second script if the one before is the same" in {
    val out = Request.removeDuplicate(List(
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
    val out = Request.removeDuplicate(List(
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