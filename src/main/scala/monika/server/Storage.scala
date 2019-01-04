package monika.server

import java.io.File

import monika.server.Model.MonikaState
import net.openhft.chronicle.hash.ChronicleHashCorruption
import net.openhft.chronicle.set.{ChronicleSet, ChronicleSetBuilder}

object Storage {

  private val stateDBFile = new File(Constants.paths.StateDB)
  private val stateDB: ChronicleSet[MonikaState] = ChronicleSetBuilder
    .of(classOf[MonikaState])
    .name("state-set")
    .entries(1)
    .averageKeySize(1024 * 10)
    .createOrRecoverPersistedTo(stateDBFile, true, onCorruption _)

  Runtime.getRuntime.addShutdownHook(new Thread(() => stateDB.close()))

  private def onCorruption(ex: ChronicleHashCorruption): Unit = {
  }

  private def queryState(): MonikaState = {
    val iter = stateDB.iterator()
    if (iter.hasNext) iter.next() else MonikaState(Vector(), None, Map.empty)
  }

  private def saveState(state: MonikaState): Unit = {
    stateDB.clear()
    stateDB.add(state)
  }

  def transaction[R](fn: MonikaState => (MonikaState, R)): R = {
    stateDB.synchronized {
      val state = queryState()
      val (newState, returnValue) = fn(state)
      saveState(newState); returnValue
    }
  }

}
