package monika.server.script

import monika.server.Structs.{Action, MonikaState}
import monika.server.UseDateTime
import monika.server.script.internal.{Brick, Freedom, Unlock}
import org.scalatest.{FlatSpec, Matchers}

class RequestBetweenSpec extends FlatSpec with Matchers with UseDateTime {

  "RequestBetween" should "reject some requests" in {
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

}
