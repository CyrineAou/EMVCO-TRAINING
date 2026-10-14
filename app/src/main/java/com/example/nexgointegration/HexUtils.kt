package com.example.nexgointegration

import java.security.MessageDigest

/** Garde uniquement les caractères hexadécimaux, en majuscules. */
fun cleanHex(s: String): String =
    s.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()

fun hexToByteArray(s: String): ByteArray {
    require(s.length % 2 == 0) { "hexa de longueur impaire" }
    return ByteArray(s.length / 2) { i ->
        s.substring(2 * i, 2 * i + 2).toInt(16).toByte()
    }
}

/** Checksum d'une CAPK : SHA-1(RID + index + modulus + exposant). */
fun capkChecksum(rid: String, index: Int, modulus: String, exponent: String): String {
    val data = hexToByteArray(rid) +
            byteArrayOf(index.toByte()) +
            hexToByteArray(modulus) +
            hexToByteArray(exponent)
    return MessageDigest.getInstance("SHA-1").digest(data)
        .joinToString("") { "%02X".format(it.toInt() and 0xFF) }
}