package monika.orbit

object MultithreadTest {

  def performAction(int: Int): Unit = {
    MultithreadTest.synchronized {
      Thread.sleep(1000)
      println("number: " + int)
    }
  }

  def main(args: Array[String]): Unit = {
    for (i ← 1 to 5) {
      new Thread(() ⇒ {
        performAction(i)
      }).start()
      Thread.sleep(10)
    }
  }

}
