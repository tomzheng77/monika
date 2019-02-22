package monika.client

import org.scalatest.{FlatSpec, Matchers}
import scala.language.postfixOps

class SignalClientSpec extends FlatSpec with Matchers {

  "The parseCommand function" should "handle this critical case" in {
    val line = "add-wheel"
    val cmd = SignalClient.parseCommand(line)
    cmd should be(Vector("add-wheel"))
  }

  it should "handle without comments" in {
    val line = "this \"is\" a command"
    val cmd = SignalClient.parseCommand(line)
    cmd should be(Vector("this", "is", "a", "command"))
  }

  it should "handle comments (1)" in {
    val line = "one command # comment here"
    val cmd = SignalClient.parseCommand(line)
    cmd should be(Vector("one", "command"))
  }

  it should "handle comments (2)" in {
    val line = "one command \"#\" comment here"
    val cmd = SignalClient.parseCommand(line)
    cmd should be(Vector("one", "command"))
  }

  it should "handle comments (3)" in {
    val line = "one command # \"comment\" here"
    val cmd = SignalClient.parseCommand(line)
    cmd should be(Vector("one", "command"))
  }

  it should "handle comments (4)" in {
    val line = "one command #comment here"
    val cmd = SignalClient.parseCommand(line)
    cmd should be(Vector("one", "command", "#comment", "here"))
  }

  it should "handle quotes" in {
    val line = "this is a \"line\""
    val cmd = SignalClient.parseCommand(line)
    cmd should be(Vector("this", "is", "a", "line"))
  }

  it should "handle quotes with spaces inside (1)" in {
    val line = "this is \"a line\""
    val cmd = SignalClient.parseCommand(line)
    cmd should be(Vector("this", "is", "a line"))
  }

  it should "handle quotes with spaces inside (2)" in {
    val line = "\"this is\" \"a line\""
    val cmd = SignalClient.parseCommand(line)
    cmd should be(Vector("this is", "a line"))
  }

  it should "handle quotes with spaces inside (3)" in {
    val line = "this \"is a\" line"
    val cmd = SignalClient.parseCommand(line)
    cmd should be(Vector("this", "is a", "line"))
  }

}
