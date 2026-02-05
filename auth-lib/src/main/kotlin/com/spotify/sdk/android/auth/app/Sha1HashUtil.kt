package com.spotify.sdk.android.auth.app

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

interface Sha1HashUtil {
    fun sha1Hash(toHash: String): String?
}

class Sha1HashUtilImpl : Sha1HashUtil {

    override fun sha1Hash(toHash: String): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            val bytes = toHash.toByteArray(Charsets.UTF_8)
            digest.update(bytes, 0, bytes.size)
            val hashedBytes = digest.digest()
            bytesToHex(hashedBytes)
        } catch (ignored: NoSuchAlgorithmException) {
            null
        }
    }

    private val HEX_ARRAY = "0123456789abcdef".toCharArray()

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF
            hexChars[j * 2] = HEX_ARRAY[v ushr 4]
            hexChars[j * 2 + 1] = HEX_ARRAY[v and 0x0F]
        }
        return String(hexChars)
    }
}
