package monika

import java.io.File

import monika.Profile.MonikaState
import net.openhft.chronicle.hash.ChronicleHashCorruption
import net.openhft.chronicle.set.{ChronicleSet, ChronicleSetBuilder}

object Storage {

  private val set: ChronicleSet[MonikaState] = ChronicleSetBuilder
    .of(classOf[MonikaState])
    .name("state-set")
    .entries(1)
    .averageKeySize(1024 * 10)
    .createOrRecoverPersistedTo(new File("state.db"), true, (corruption: ChronicleHashCorruption) => {
      println(corruption.message())
    })

  Runtime.getRuntime.addShutdownHook(new Thread(() => set.close()))

  private def queryState(): MonikaState = {
    val iter = set.iterator()
    if (iter.hasNext) iter.next() else MonikaState(Vector(), None)
  }

  private def saveState(state: MonikaState): Unit = {
    set.clear()
    set.add(state)
  }

  def transaction[R](fn: MonikaState => (MonikaState, R)): R = {
    set.synchronized {
      val state = queryState()
      val (newState, returnValue) = fn(state)
      saveState(newState); returnValue
    }
  }

}
