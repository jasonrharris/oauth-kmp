package com.baloomba.oauth.core.oauth

internal object UrlEncoding {
    private const val HEX = "0123456789ABCDEF"

    /** RFC 3986 percent-encoding; only unreserved characters are left as is. */
    fun encode(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val b = byte.toInt() and 0xFF
            val c = b.toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '.' || c == '_' || c == '~') {
                append(c)
            } else {
                append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
            }
        }
    }

    /** Decodes percent escapes and `+` as space. Returns null on a malformed escape. */
    fun decode(value: String): String? {
        val out = ArrayList<Byte>(value.length)
        val src = value.encodeToByteArray()
        var i = 0
        while (i < src.size) {
            val c = src[i].toInt().toChar()
            when (c) {
                '+' -> out += ' '.code.toByte()
                '%' -> {
                    if (i + 2 >= src.size) return null
                    val hi = hexValue(src[i + 1].toInt().toChar())
                    val lo = hexValue(src[i + 2].toInt().toChar())
                    if (hi < 0 || lo < 0) return null
                    out += ((hi shl 4) or lo).toByte()
                    i += 2
                }
                else -> out += src[i]
            }
            i++
        }
        return out.toByteArray().decodeToString()
    }

    private fun hexValue(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }
}
