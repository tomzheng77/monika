package monika.server

import java.io.File

import monika.server.pure.Model.MonikaState
import net.openhft.chronicle.hash.ChronicleHashCorruption
import net.openhft.chronicle.set.{ChronicleSet, ChronicleSetBuilder}
import org.slf4j.{Logger, LoggerFactory}

/**
  * - persists a single MonikaState to a file
  * - provides a method to perform a stateful transaction on MonikaState
  * - reports any errors to log
  */
object StateStore {

  private val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  private val stateDBFile = new File(Constants.Locations.SavedState)
  private val stateDB: ChronicleSet[MonikaState] = ChronicleSetBuilder
    .of(classOf[MonikaState])
    .name("state-set")
    .entries(1)
    .averageKeySize(1024 * 10)
    .createOrRecoverPersistedTo(stateDBFile, true, onCorruption _)

  Runtime.getRuntime.addShutdownHook(new Thread(() => stateDB.close()))

  def transaction[R](fn: MonikaState => (MonikaState, R)): R = {
    stateDB.synchronized {
      val state = queryState()
      val (newState, returnValue) = fn(state)
      saveState(newState); returnValue
    }
  }

  private def onCorruption(ex: ChronicleHashCorruption): Unit = {
    LOGGER.error(ex.message() + s" (segment: ${ex.segmentIndex()})", ex.exception())
  }

  private def queryState(): MonikaState = {
    val iter = stateDB.iterator()
    if (iter.hasNext) iter.next() else MonikaState(Vector(), None, Map.empty, Map.empty)
  }

  private def saveState(state: MonikaState): Unit = {
    stateDB.clear()
    stateDB.add(state)
  }

}
