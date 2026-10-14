object DataConverter {

    // 1. Byte Array -> Hex String
    fun byteArrayToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }

    // 2. Hex String -> Byte Array
    fun hexToByteArray(hex: String): ByteArray {
        val cleanHex = if (hex.length % 2 != 0) "0$hex" else hex
        return cleanHex.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }

    // 3. Byte Array -> Binary String
    fun byteArrayToBinary(bytes: ByteArray): String {
        return bytes.joinToString("") { byte ->
            String.format("%8s", Integer.toBinaryString(byte.toInt() and 0xFF)).replace(' ', '0')
        }
    }

    // 4. Binary String -> Byte Array
    fun binaryToByteArray(binary: String): ByteArray {
        val paddedBinary = if (binary.length % 8 != 0) {
            "0".repeat(8 - (binary.length % 8)) + binary
        } else {
            binary
        }
        return paddedBinary.chunked(8)
            .map { it.toInt(2).toByte() }
            .toByteArray()
    }

    fun String.hexToBytes(): ByteArray {
        val s = this.replace(" ", "")
        require(s.length % 2 == 0) { "Longueur hexa impaire" }
        return ByteArray(s.length / 2) { i ->
            s.substring(2 * i, 2 * i + 2).toInt(16).toByte()
        }
    }

    fun ByteArray.toHex(): String =
        joinToString("") { "%02X".format(it.toInt() and 0xFF) }
}