package org.simple.clinic.util

import javax.crypto.KeyGenerator

object CryptoUtils {

  fun generateEncodedKey(algorithm: String, keySize: Int): ByteArray {
    val keyGenerator = KeyGenerator.getInstance(algorithm)
    keyGenerator.init(keySize)
    return keyGenerator.generateKey().encoded
  }
}
