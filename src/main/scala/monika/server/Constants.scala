package monika.server

import java.io.File

import monika.Primitives._
import scalaz.@@

object Constants {

  /**
    * additional paths to prepend to the PATH variable
    * in order to locate programs required by the profile
    * and monika itself
    */
  val PathAdd: Vector[String @@ FilePath] = Vector(
    "/usr/sbin"
  ).map(FilePath)

  val PathOriginal: String = System.getenv("PATH")
  val Path: String = PathAdd.mkString(File.pathSeparator) + File.pathSeparator + PathOriginal
  val PathList: Vector[String] = Path.split(File.pathSeparatorChar).toVector

  val GlobalEncoding = "UTF-8"
  val ProxyPort = 9000
  val InterpreterPort = 9001
  val MaxQueueSize = 3
  val MonikaUser = "tomzheng"

  /**
    * programs which may be requested by the Profile
    * a group should be created for each of the programs e.g. use-studio
    */
  val ProfilePrograms: Vector[String @@ FileName] = Vector(
    "studio",
    "subl",
    "idea",
    "firefox",
    "google-chrome",
    "steam",
    "virtualbox",
    "jetbrains-toolbox",
    "wine",
    "libreoffice",
    "ssh",
    "assistant.jar"
  ).map(FileName)

  /**
    * programs which may be called by monika
    * these constants should be referenced instead of
    * hardcoding the name of each program
    */
  object CallablePrograms {
    val id: String @@ FileName = FileName("id")
    val groups: String @@ FileName = FileName("groups")
    val passwd: String @@ FileName = FileName("passwd")
    val chmod: String @@ FileName = FileName("chmod")
    val chown: String @@ FileName = FileName("chown")
    val iptables: String @@ FileName = FileName("iptables")
    val usermod: String @@ FileName = FileName("usermod")
    val groupadd: String @@ FileName = FileName("groupadd")
    val killall: String @@ FileName = FileName("killall")
    val asList = List(passwd, chmod, chown, iptables, usermod, groupadd, killall)
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
