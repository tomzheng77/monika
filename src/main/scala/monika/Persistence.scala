package monika

import java.io.File

import monika.Profile.MonikaState
import net.openhft.chronicle.hash.ChronicleHashCorruption
import net.openhft.chronicle.set.{ChronicleSet, ChronicleSetBuilder}

object Persistence {

  private val set: ChronicleSet[MonikaState] = ChronicleSetBuilder
    .of(classOf[MonikaState])
    .name("state-set")
    .entries(1)
    .averageKeySize(1024 * 10)
    .createOrRecoverPersistedTo(new File("state.db"), true, (corruption: ChronicleHashCorruption) => {
      println(corruption.message())
    })

  def queryState(): MonikaState = {
    val iter = set.iterator()
    if (iter.hasNext) iter.next() else MonikaState(Vector(), None)
  }

  def saveState(state: MonikaState): Unit = {
    set.clear()
    set.add(state)
  }

  def modifyState(fn: MonikaState => MonikaState): Unit = {
    val state = queryState()
    saveState(fn(state))
  }

}
