package dev.lex.editor.data

import java.nio.charset.Charset

/**
 * The charset Lex believes a file is written in, plus the byte-order mark it found.
 *
 * [bomLength] is the number of leading bytes that must be skipped before decoding and re-emitted
 * verbatim on save; it is 0 whenever [hasBom] is false.
 */
data class DetectedEncoding(
    val charsetName: String,
    val hasBom: Boolean,
    val bomLength: Int,
)

/**
 * Charset sniffing for text documents.
 *
 * Deliberately pure Kotlin/JVM — no android imports — so the whole decision table is covered by
 * plain JUnit tests. The strategy, in order: byte-order mark, strict UTF-8 validation, NUL-density
 * heuristic for BOM-less UTF-16, GBK shape check, and finally ISO-8859-1, which decodes any byte
 * sequence without throwing and so guarantees the editor always opens *something*.
 */
object EncodingDetector {

    /** Above this the editor would blow the heap holding bytes + String + Compose text state. */
    const val MAX_FILE_BYTES: Long = 4L * 1024 * 1024

    private const val UTF_8 = "UTF-8"
    private const val UTF_16LE = "UTF-16LE"
    private const val UTF_16BE = "UTF-16BE"
    private const val UTF_32LE = "UTF-32LE"
    private const val UTF_32BE = "UTF-32BE"
    private const val GBK = "GBK"
    private const val LATIN_1 = "ISO-8859-1"

    /** How much of a large file to inspect for the BOM-less heuristics. */
    private const val SAMPLE_BYTES = 64 * 1024

    fun detect(bytes: ByteArray): DetectedEncoding {
        detectBom(bytes)?.let { return it }

        val sample = if (bytes.size > SAMPLE_BYTES) bytes.copyOf(SAMPLE_BYTES) else bytes

        // The UTF-16 heuristic has to run *before* the UTF-8 validator, but only for content that
        // actually contains NUL bytes: NUL is a perfectly legal UTF-8 encoding of U+0000, so
        // BOM-less ASCII-in-UTF-16 would otherwise validate as UTF-8 and decode to a string with a
        // NUL between every letter. Real text files contain no NULs, so this costs nothing.
        if (sample.any { it.toInt() == 0 }) {
            guessUtf16ByNulPattern(sample)?.let {
                return DetectedEncoding(supported(it), hasBom = false, bomLength = 0)
            }
        }

        // A BOM-less file that validates as UTF-8 is UTF-8: pure ASCII validates too, which is
        // why ASCII is reported as UTF-8 (a superset that round-trips it byte for byte).
        if (isStrictUtf8(bytes)) return DetectedEncoding(supported(UTF_8), hasBom = false, bomLength = 0)

        val fallback = if (looksLikeGbk(sample)) GBK else LATIN_1
        return DetectedEncoding(supported(fallback), hasBom = false, bomLength = 0)
    }

    // ---------------------------------------------------------------- BOM

    private fun detectBom(b: ByteArray): DetectedEncoding? {
        // UTF-32LE must be tested before UTF-16LE: they share the FF FE prefix.
        if (b.startsWith(0xFF, 0xFE, 0x00, 0x00)) return bom(UTF_32LE, 4)
        if (b.startsWith(0x00, 0x00, 0xFE, 0xFF)) return bom(UTF_32BE, 4)
        if (b.startsWith(0xEF, 0xBB, 0xBF)) return bom(UTF_8, 3)
        if (b.startsWith(0xFF, 0xFE)) return bom(UTF_16LE, 2)
        if (b.startsWith(0xFE, 0xFF)) return bom(UTF_16BE, 2)
        return null
    }

    private fun bom(charsetName: String, length: Int): DetectedEncoding {
        val name = supported(charsetName)
        return DetectedEncoding(name, hasBom = true, bomLength = length)
    }

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if ((this[i].toInt() and 0xFF) != prefix[i]) return false
        return true
    }

    /** The BOM bytes for [charsetName], or an empty array for encodings that have none. */
    fun bomFor(charsetName: String): ByteArray = when (canonical(charsetName)) {
        UTF_8 -> byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        UTF_16LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        UTF_16BE -> byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        UTF_32LE -> byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)
        UTF_32BE -> byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())
        else -> ByteArray(0)
    }

    // ---------------------------------------------------------------- UTF-8

    private fun isContinuation(b: Byte): Boolean = (b.toInt() and 0xC0) == 0x80

    /**
     * RFC 3629 validation: rejects overlong forms (C0/C1, E0 80.., F0 80..), UTF-16 surrogates
     * encoded as three bytes (ED A0..BF ..) and anything past U+10FFFF (F4 90.. and F5..FF).
     * Being strict is the point — a lenient check would happily accept GBK or Latin-1 text.
     */
    private fun isStrictUtf8(bytes: ByteArray): Boolean {
        var i = 0
        val n = bytes.size
        while (i < n) {
            val b0 = bytes[i].toInt() and 0xFF
            when {
                b0 <= 0x7F -> i++

                b0 in 0xC2..0xDF -> {
                    if (i + 1 >= n || !isContinuation(bytes[i + 1])) return false
                    i += 2
                }

                b0 == 0xE0 -> {
                    if (i + 2 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (b1 !in 0xA0..0xBF) return false // overlong two-byte value
                    if (!isContinuation(bytes[i + 2])) return false
                    i += 3
                }

                b0 == 0xED -> {
                    if (i + 2 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (b1 !in 0x80..0x9F) return false // U+D800..U+DFFF surrogate
                    if (!isContinuation(bytes[i + 2])) return false
                    i += 3
                }

                b0 in 0xE1..0xEF -> {
                    if (i + 2 >= n) return false
                    if (!isContinuation(bytes[i + 1]) || !isContinuation(bytes[i + 2])) return false
                    i += 3
                }

                b0 == 0xF0 -> {
                    if (i + 3 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (b1 !in 0x90..0xBF) return false // overlong three-byte value
                    if (!isContinuation(bytes[i + 2]) || !isContinuation(bytes[i + 3])) return false
                    i += 4
                }

                b0 == 0xF4 -> {
                    if (i + 3 >= n) return false
                    val b1 = bytes[i + 1].toInt() and 0xFF
                    if (b1 !in 0x80..0x8F) return false // beyond U+10FFFF
                    if (!isContinuation(bytes[i + 2]) || !isContinuation(bytes[i + 3])) return false
                    i += 4
                }

                b0 in 0xF1..0xF3 -> {
                    if (i + 3 >= n) return false
                    if (!isContinuation(bytes[i + 1]) ||
                        !isContinuation(bytes[i + 2]) ||
                        !isContinuation(bytes[i + 3])
                    ) return false
                    i += 4
                }

                // 0x80..0xC1 (stray continuation / overlong lead) and 0xF5..0xFF.
                else -> return false
            }
        }
        return true
    }

    // ---------------------------------------------------------------- UTF-16 without a BOM

    /**
     * Mostly-Latin UTF-16 text is half NUL bytes, and they all land on the same parity: the high
     * byte of each unit sits at an odd offset in little-endian and an even offset in big-endian.
     */
    private fun guessUtf16ByNulPattern(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        var nulAtEven = 0
        var nulAtOdd = 0
        // Ignore a trailing odd byte so both parities cover the same number of positions.
        val n = bytes.size and 1.inv()
        for (i in 0 until n) {
            if (bytes[i].toInt() == 0) {
                if (i and 1 == 0) nulAtEven++ else nulAtOdd++
            }
        }
        val units = n / 2
        val threshold = units / 2 // at least half the units carry a NUL high byte
        return when {
            nulAtOdd >= threshold && nulAtOdd > nulAtEven * 4 -> UTF_16LE
            nulAtEven >= threshold && nulAtEven > nulAtOdd * 4 -> UTF_16BE
            else -> null
        }
    }

    // ---------------------------------------------------------------- GBK

    /**
     * GBK is the common legacy encoding for Simplified Chinese text files. Every non-ASCII byte
     * must open a well-formed double-byte pair; one stray byte and we prefer the lossless
     * ISO-8859-1 fallback over mangling the document.
     */
    private fun looksLikeGbk(bytes: ByteArray): Boolean {
        var i = 0
        var pairs = 0
        while (i < bytes.size) {
            val lead = bytes[i].toInt() and 0xFF
            if (lead <= 0x7F) {
                i++
                continue
            }
            if (lead < 0x81) return false // 0x80 is not a GBK lead byte
            if (i + 1 >= bytes.size) return false
            val trail = bytes[i + 1].toInt() and 0xFF
            if (trail < 0x40 || trail > 0xFE || trail == 0x7F) return false
            pairs++
            i += 2
        }
        return pairs > 0
    }

    // ---------------------------------------------------------------- charset availability

    private fun canonical(name: String): String = when (name.uppercase()) {
        "UTF8", "UTF-8" -> UTF_8
        // Bare "UTF-16" makes the JDK encoder emit its own BOM; pin the byte order instead.
        "UTF-16", "UTF16", "UTF-16BE" -> UTF_16BE
        "UTF-16LE" -> UTF_16LE
        "UTF-32", "UTF32", "UTF-32BE" -> UTF_32BE
        "UTF-32LE" -> UTF_32LE
        else -> name
    }

    /**
     * Android ships a trimmed charset set (UTF-32 in particular is not guaranteed), so every name
     * we hand back must be one `Charset.forName` will actually accept.
     */
    private fun supported(name: String): String {
        val canonical = canonical(name)
        return try {
            if (Charset.isSupported(canonical)) canonical else UTF_8
        } catch (e: IllegalArgumentException) {
            // Illegal or unsupported charset name — either way UTF-8 is the safe default.
            UTF_8
        }
    }

    /** Resolves [charsetName] to a usable [Charset], falling back to UTF-8. */
    fun charsetOf(charsetName: String): Charset = Charset.forName(supported(charsetName))
}
