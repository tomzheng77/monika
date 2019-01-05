package monika.server.persist

import java.io.File

import monika.server.Constants
import monika.server.pure.Model.{InitialState, MonikaState}
import net.openhft.chronicle.hash.ChronicleHashCorruption
import net.openhft.chronicle.set.{ChronicleSet, ChronicleSetBuilder}
import org.slf4j.{Logger, LoggerFactory}

/**
  * - persists a single MonikaState to disk
  * - provides a method to perform a stateful transaction on MonikaState
  * - reports any errors to log
  */
object StateStore {

  private val LOGGER: Logger = LoggerFactory.getLogger(getClass)

  private val stateDBFile = new File(Constants.Locations.SavedState)
  private val stateDB: ChronicleSet[MonikaState] = openStateDB(stateDBFile)
  Runtime.getRuntime.addShutdownHook(new Thread(() => stateDB.close()))

  def transaction[R](fn: MonikaState => (MonikaState, R)): R = {
    stateDB.synchronized {
      val state = queryState()
      val (newState, returnValue) = fn(state)
      saveState(newState); returnValue
    }
  }

  private def openStateDB(file: File): ChronicleSet[MonikaState] = {
    val builder = ChronicleSetBuilder
      .of(classOf[MonikaState])
      .name("monika-state")
      .entries(1)
      .averageKeySize(1024 * 10)

    if (!file.exists()) {
      LOGGER.info(s"DB file ${file.getCanonicalPath} not found, starting from new")
      builder.createPersistedTo(file)
    } else if (!(file.canRead & file.canWrite)) {
      val message = s"DB file ${file.getCanonicalPath} must be readable and writable"
      LOGGER.error(message)
      throw new RuntimeException(message)
    } else {
      def onCorruption(ex: ChronicleHashCorruption): Unit = {
        LOGGER.error("corruption: " + ex.message() + s" (segment: ${ex.segmentIndex()})", ex.exception())
      }
      LOGGER.error(s"recovering from DB file ${file.getCanonicalPath}")
      builder.recoverPersistedTo(file, true, onCorruption _)
    }
  }

  private def queryState(): MonikaState = {
    val iter = stateDB.iterator()
    if (iter.hasNext) iter.next() else InitialState
  }

  private def saveState(state: MonikaState): Unit = {
    stateDB.clear()
    stateDB.add(state)
  }

}
