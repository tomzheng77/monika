package monika.orbit

import org.scalacheck.Arbitrary._
import org.scalacheck.Prop.forAll
import org.scalacheck.Properties

object OrbitEncryptionSpec extends Properties("OrbitEncryption") with OrbitEncryption {

  property("encrypt and decrypt") = {
    forAll(arbitrary[String])(a => {
      decryptAES(encryptAES(a)) == a
    })
  }

  property("encrypt and decrypt with salt") = {
    forAll(arbitrary[String])(a => {
      decryptPBE(encryptPBE(a)) == a
    })
  }

}
