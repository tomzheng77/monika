package monika.server

import monika.server.Model.FileName
import scalaz.@@

object Constants {

  val MonikaHome: String = System.getenv("MONIKA_HOME")
  if (MonikaHome == null) {
    throw new RuntimeException("MONIKA_HOME not found, please check /etc/environment")
  }

  val Users = Vector(
    "tomzheng",
    "profile",
    "unlocker"
  )

  val GlobalEncoding = "UTF-8"
  val MainUser = "tomzheng"
  val ProfileUser = "profile"
  val ProxyPort = 9000
  val InterpreterPort = 9001
  val MaxQueueSize = 3

  val UserPrograms: Vector[String @@ FileName] = Vector(
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

  object programs {
    val passwd: String @@ FileName = FileName("passwd")
    val chmod: String @@ FileName = FileName("chmod")
    val chown: String @@ FileName = FileName("chown")
    val iptables: String @@ FileName = FileName("iptables")
    val usermod: String @@ FileName = FileName("usermod")
    val groupadd: String @@ FileName = FileName("groupadd")
    val asList = List(passwd, chmod, chown, iptables, usermod, groupadd)
  }

  object paths {

    val StateDB: String = MonikaHome + "/state.db"
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
