package monika.orbit

import java.time.LocalDateTime

import org.scalatest.{FlatSpec, Matchers}

class OrbitSpec extends FlatSpec with Matchers {

  it should "add normal confirm" in {
    val time = LocalDateTime.of(2019, 2, 24, 21, 0, 0)
    var state = Domain.initialState
    state = Domain.handle(Vector("add-confirm", "A", "2019-02-24", "23:00", "10"))(time)(state)._1
    state.confirms.length should be(1)
  }

  it should "confirm normal confirm" in {
    val time = LocalDateTime.of(2019, 2, 24, 21, 0, 0)
    var state = Domain.initialState
    state = Domain.handle(Vector("add-confirm", "A", "2019-02-24", "23:00", "10"))(time)(state)._1
    state.confirms.length should be(1)

    val confirmTime = LocalDateTime.of(2019, 2, 24, 22, 55, 0)
    state = Domain.handle(Vector("confirm", "A"))(confirmTime)(state)._1
    state.confirms.length should be(0)
  }

  it should "not confirm normal confirm if outside window" in {
    val time = LocalDateTime.of(2019, 2, 24, 21, 0, 0)
    var state = Domain.initialState
    state = Domain.handle(Vector("add-confirm", "A", "2019-02-24", "23:00", "10"))(time)(state)._1
    state.confirms.length should be(1)

    val confirmTime = LocalDateTime.of(2019, 2, 24, 22, 40, 0)
    state = Domain.handle(Vector("confirm", "A"))(confirmTime)(state)._1
    state.confirms.length should be(1)
  }

  it should "not add confirm if the key does not exist" in {
    val time = LocalDateTime.of(2019, 2, 24, 21, 0, 0)
    var state = Domain.initialState
    state = Domain.handle(Vector("add-confirm", "A", "2019-02-24", "23:00", "10", "keyA"))(time)(state)._1
    state.confirms.length should be(0)
  }

  it should "add confirm if the key does exist" in {
    val time = LocalDateTime.of(2019, 2, 24, 21, 0, 0)
    var state = Domain.initialState
    state = Domain.handle(Vector("add-key", "keyA", "valueA"))(time)(state)._1
    state = Domain.handle(Vector("add-confirm", "A", "2019-02-24", "23:00", "10", "keyA"))(time)(state)._1
    state.confirms.length should be(1)
  }

  it should "not confirm confirm with key if the key is not provided" in {
    val time = LocalDateTime.of(2019, 2, 24, 21, 0, 0)
    var state = Domain.initialState
    state = Domain.handle(Vector("add-key", "keyA", "valueA"))(time)(state)._1
    state = Domain.handle(Vector("add-confirm", "A", "2019-02-24", "23:00", "10", "keyA"))(time)(state)._1
    state.confirms.length should be(1)

    val confirmTime = LocalDateTime.of(2019, 2, 24, 22, 55, 0)
    state = Domain.handle(Vector("confirm", "A"))(confirmTime)(state)._1
    state.confirms.length should be(1)
  }

  it should "not confirm confirm with key if the key does not match" in {
    val time = LocalDateTime.of(2019, 2, 24, 21, 0, 0)
    var state = Domain.initialState
    state = Domain.handle(Vector("add-key", "keyA", "valueA"))(time)(state)._1
    state = Domain.handle(Vector("add-confirm", "A", "2019-02-24", "23:00", "10", "keyA"))(time)(state)._1
    state.confirms.length should be(1)

    val confirmTime = LocalDateTime.of(2019, 2, 24, 22, 55, 0)
    state = Domain.handle(Vector("confirm", "A", "valueB"))(confirmTime)(state)._1
    state.confirms.length should be(1)
  }

  it should "confirm confirm with key if the key does match" in {
    val time = LocalDateTime.of(2019, 2, 24, 21, 0, 0)
    var state = Domain.initialState
    state = Domain.handle(Vector("add-key", "keyA", "valueA"))(time)(state)._1
    state = Domain.handle(Vector("add-confirm", "A", "2019-02-24", "23:00", "10", "keyA"))(time)(state)._1
    state.confirms.length should be(1)

    val confirmTime = LocalDateTime.of(2019, 2, 24, 22, 55, 0)
    state = Domain.handle(Vector("confirm", "A", "valueA"))(confirmTime)(state)._1
    state.confirms.length should be(0)
  }

  it should "add notes" in {
    val time = LocalDateTime.of(2019, 2, 24, 21, 0, 0)
    var state = Domain.initialState
    state = Domain.handle(Vector("add-note", "noteA"))(time)(state)._1
    state.notes.length should be(1)

    state = Domain.handle(Vector("add-note", "noteB"))(time)(state)._1
    state = Domain.handle(Vector("add-note", "noteC"))(time)(state)._1
    state.notes.length should be(3)
  }

}
