package monika

import java.io.File

import net.openhft.chronicle.hash.ChronicleHashCorruption
import net.openhft.chronicle.map.{ChronicleMap, ChronicleMapBuilder}
import net.openhft.chronicle.set.{ChronicleSet, ChronicleSetBuilder}

object ChronicleTest {

  def main(args: Array[String]): Unit = {
    val cset: ChronicleSet[String] = ChronicleSetBuilder
      .of(classOf[String])
      .name("string-set")
      .averageKey("some-string")
      .entries(10000)
      .createOrRecoverPersistedTo(new File("set.db"), true, (corruption: ChronicleHashCorruption) => {
        println(corruption.message())
      })

    val cmap: ChronicleMap[String, String] = ChronicleMapBuilder
      .of(classOf[String], classOf[String])
      .name("string-to-string")
      .averageKey("some-string")
      .averageValue("another-string")
      .entries(20000)
      .createOrRecoverPersistedTo(new File("map.db"), true, (corruption: ChronicleHashCorruption) => {
        println(corruption.message())
      })

    for (i <- 0 until 20000) {
      cmap.put(s"key$i", s"value$i")
    }
    cmap.put(Array.fill(10000)("string").mkString, "")
  }

}
