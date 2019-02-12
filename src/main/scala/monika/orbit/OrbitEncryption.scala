package monika.orbit

import com.google.common.io.BaseEncoding.base64
import javax.crypto.spec.{PBEKeySpec, PBEParameterSpec, SecretKeySpec}
import javax.crypto.{Cipher, SecretKeyFactory}

trait OrbitEncryption {

  private val SecretKey = "Tp]+6FqOxj[G4.&7]Qp8GDjAvUP+[eq7"
  private val SecretKeyChars = SecretKey.toCharArray
  private val Encoding = "UTF-8"
  private val Salt = Array(0xde.toByte, 0x33.toByte, 0x10.toByte, 0x12.toByte, 0xde.toByte, 0x33.toByte, 0x10.toByte, 0x12.toByte)

  protected def encrypt(in: String): String = {
    val aesKey = new SecretKeySpec(SecretKey.getBytes(Encoding), "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, aesKey)
    base64().encode(cipher.doFinal(in.getBytes(Encoding)))
  }

  protected def decrypt(in: String): String = {
    val cipher = Cipher.getInstance("AES")
    val aesKey = new SecretKeySpec(SecretKey.getBytes(Encoding), "AES")
    cipher.init(Cipher.DECRYPT_MODE, aesKey)
    new String(cipher.doFinal(base64().decode(in)), Encoding)
  }

  protected def encryptWithSalt(in: String): String = {
    val keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES")
    val key = keyFactory.generateSecret(new PBEKeySpec(SecretKeyChars))
    val pbeCipher = Cipher.getInstance("PBEWithMD5AndDES")
    pbeCipher.init(Cipher.ENCRYPT_MODE, key, new PBEParameterSpec(Salt, 20))
    base64().encode(pbeCipher.doFinal())
  }

  protected def decryptWithSalt(in: String): String = {
    val keyFactory = SecretKeyFactory.getInstance("PBEWithMD5AndDES")
    val key = keyFactory.generateSecret(new PBEKeySpec(SecretKeyChars))
    val pbeCipher = Cipher.getInstance("PBEWithMD5AndDES")
    pbeCipher.init(Cipher.DECRYPT_MODE, key, new PBEParameterSpec(Salt, 20))
    new String(pbeCipher.doFinal(base64.decode(in)), Encoding)
  }

}
