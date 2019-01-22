package monika.server

import java.io.File
import java.nio.charset.Charset

import monika.Primitives._
import scalaz.@@

object Constants {

  /**
    * additional paths to prepend to the PATH variable
    * in order to locate programs required by the profile
    * and monika itself
    */
  val PathAdd: Vector[String @@ FilePath] = Vector(
    "/usr/bin",
    "/usr/sbin",
    "/usr/local/bin"
  ).map(FilePath)

  val PathOriginal: String = System.getenv("PATH")
  val Path: String = PathAdd.mkString(File.pathSeparator) + File.pathSeparator + PathOriginal
  val PathList: Vector[String] = Path.split(File.pathSeparatorChar).toVector

  val GlobalEncoding = "UTF-8"
  val GlobalCharset: Charset = Charset.forName("UTF-8")

  val ProxyPort = 9000
  val InterpreterPort = 9001
  val MaxQueueSize = 3
  val MonikaUser = "tomzheng"

  object Restricted {

    /**
      * programs organised by launcher name and core-file path
      * the launcher name is the name of the script inside PATH
      * - upon lock, it will be set to root:root 700
      * - upon unlock, it will be set to root:root 755
      * - killall will be run on the resolved location
      *
      * the core-file path is the path to a file required by the ongoing process
      * once the program has been launched
      * - upon lock, it will be set to root:root 700
      * - upon unlock, it will be set to root:root 755
      * - killall will be run on this path
      */
    val Programs: Map[String @@ FileName, String @@ FilePath] = Vector(
      "studio" -> "/opt/android-studio/jre/bin/java",
      "subl" -> "/opt/sublime_text/sublime_text",
      "idea" -> "/opt/JetBrains/Toolbox/apps/IDEA-U/ch-0/182.4892.20/jre64/bin/java",
      "firefox" -> "/usr/lib64/firefox/firefox",
      "google-chrome" -> "/opt/google/chrome/chrome",
      "steam" -> "/usr/bin/steam",
      "virtualbox" -> "/usr/lib/virtualbox/VirtualBox",
      "wine" -> "/usr/bin/wine64-preloader",
      "libreoffice" -> "/usr/lib64/libreoffice/program/soffice.bin",
      "ssh" -> "/usr/bin/ssh",
      "assistant.jar" -> "/usr/local/bin/assistant.jar",
      "arduino" -> "/usr/bin/arduino"
    ).map(pair => FileName(pair._1) -> FilePath(pair._2)).toMap

    val Projects: Vector[String @@ FilePath] = Vector(
      Locations.ProjectRoot,
      "/home/tomzheng/Documents/Projects"
    ).map(FilePath)

  }

  object Locations {

    val MonikaHome: String = System.getenv("MONIKA_HOME")
    if (MonikaHome == null) {
      throw new RuntimeException("MONIKA_HOME not found, please check /etc/environment")
    }

    val PrimaryLog: String = MonikaHome + "/monika.log"
    val StateJsonFile: String = MonikaHome + "/monika.json"

    val ProfileRoot: String = MonikaHome + "/profiles"
    val ProjectRoot: String = MonikaHome + "/projects"
    val ChromeBookmark: String = "/home/profile/.config/google-chrome/Default/Bookmarks"

    // Google Chrome -> Manage Certificates -> Authorities -> Import (certificate.cer), Trust *
    // create a CA Root Certificate using default settings
    // use a previously generated keystore.p12 file
    val CertificateRoot: String = MonikaHome + "/certs"
    val Certificate: String = CertificateRoot + "/certificate.cer"
    val PrivateKey: String = CertificateRoot + "/private-key.pem"
    val KeyStore: String = CertificateRoot + "/keystore.p12"

  }

}
