package monika.orbit

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

trait OrbitEncryption {

  private val SecretKey = "%%QLWaMKt2w^MWxN]H&0"
  private val Encoding = "UTF-8"

  protected def encrypt(in: String): String = {
    val aesKey = new SecretKeySpec(SecretKey.getBytes(Encoding), "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, aesKey)
    new String(cipher.doFinal(in.getBytes(Encoding)), Encoding)
  }

  protected def decrypt(in: String): String = {
    val cipher = Cipher.getInstance("AES")
    val aesKey = new SecretKeySpec(SecretKey.getBytes(Encoding), "AES")
    cipher.init(Cipher.DECRYPT_MODE, aesKey)
    new String(cipher.doFinal(in.getBytes(Encoding)), Encoding)
  }

}
