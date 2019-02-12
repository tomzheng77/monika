package monika.orbit

import org.json4s.jackson.JsonMethods._
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop.forAll
import org.scalacheck.Properties
import org.json4s.JsonDSL._

object OrbitEncryptionSpec extends Properties("OrbitEncryption") with OrbitEncryption {

  property("encrypt and decrypt") = {
    forAll(arbitrary[String])(a => {
      decrypt(encrypt(a)) == a
    })
  }

  property("encrypt and decrypt with salt") = {
    forAll(arbitrary[String])(a => {
      println(pretty(render(a)))
      println(pretty(render(encryptWithSalt(a))))
      println(pretty(render(decryptWithSalt(encryptWithSalt(a)))))
      decryptWithSalt(encryptWithSalt(a)) == a
    })
  }

}
