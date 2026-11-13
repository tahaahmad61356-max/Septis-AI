package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom

object CryptoUtils {
    private val secureRandom = SecureRandom()

    fun generateSaltHex(byteLength: Int = 16): String {
        val bytes = ByteArray(byteLength)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun hashSecret(salt: String, secret: String): String {
        return sha256Hex("$salt:$secret")
    }
}
