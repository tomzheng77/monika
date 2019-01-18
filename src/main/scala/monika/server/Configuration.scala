package monika.server

import java.io.File

import monika.server.Constants.Locations
import monika.server.Structs.Profile
import org.apache.commons.io.FileUtils

object Configuration extends UseJSON {

  def readProfileDefinitions(): Map[String, Profile] = {
    import scala.collection.JavaConverters._
    val jsonFiles: Vector[File] = {
      val profileRoot = new File(Locations.ProfileRoot)
      if (!profileRoot.exists()) Vector.empty
      else FileUtils.listFiles(new File(Locations.ProfileRoot), Array("json"), true).asScala.toVector
    }
    jsonFiles.map(readJSONToItem[Profile](_)).map(p => p.name -> p).toMap
  }

}
