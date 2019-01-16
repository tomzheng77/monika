package monika.server

import java.io.File

import monika.Primitives.FileName
import monika.server.Constants.Locations
import monika.server.LittleProxy.ProxySettings
import monika.server.Structs.{Bookmark, Profile}
import org.apache.commons.io.FileUtils

object Configuration extends UseJSON {

  def readProfileDefinitions(): Map[String, Profile] = {
    import scala.collection.JavaConverters._
    val jsonFiles: Vector[File] = {
      val profileRoot = new File(Locations.ProfileRoot)
      if (!profileRoot.exists()) Vector.empty
      else FileUtils.listFiles(new File(Locations.ProfileRoot), Array("json"), true).asScala.toVector
    }
    val jsons = jsonFiles.map(parseJSON(_))
    jsons.map(convertJsonToProfile).map(p => p.name -> p).toMap
  }

  /**
    * constructs a profile from a .json definition file
    * this is not a deserialization process, it is fault tolerant and provides
    * default values for all fields except name
    */
  private def convertJsonToProfile(definition: JValue): Profile = {
    Profile(
      (definition \ "name").extract[String],
      (definition \ "programs").extractOpt[Vector[String]].getOrElse(Vector.empty).map(FileName),
      (definition \ "projects").extractOpt[Vector[String]].getOrElse(Vector.empty).map(FileName),
      (definition \ "bookmarks").extractOpt[Vector[JValue]].getOrElse(Vector.empty).map(v => {
        val url = (v \ "url").extractOpt[String].getOrElse("http://www.google.com")
        val re = "[A-Za-z-]+(\\.[A-Za-z-]+)*\\.[A-Za-z-]+".r
        val name = (v \ "name").extractOpt[String].orElse(re.findFirstIn(url)).getOrElse("Unknown")
        Bookmark(name, url)
      }),
      ProxySettings(
        (definition \ "proxy" \ "transparent").extractOpt[Boolean].getOrElse(false),
        (definition \ "proxy" \ "allowHtmlPrefix").extractOpt[Vector[String]].getOrElse(Vector.empty),
        (definition \ "proxy" \ "rejectHtmlKeywords").extractOpt[Vector[String]].getOrElse(Vector.empty)
      )
    )
  }

}
