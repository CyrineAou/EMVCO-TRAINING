package com.example.worldlineintegrationsdk.tlp

import java.io.InputStream

/**
 * Lecteur tar minimal (sans dépendance). Gère les fichiers normaux, les noms longs GNU ('L')
 * et ignore les en-têtes pax ('x', 'g'). À utiliser avec un GZIPInputStream pour un .tar.gz.
 */
object TarReader {

    private const val BLOCK = 512

    /** Renvoie (nom, contenu) pour chaque fichier normal de l'archive. */
    fun entries(input: InputStream): Sequence<Pair<String, ByteArray>> = sequence {
        val header = ByteArray(BLOCK)
        var longName: String? = null

        while (true) {
            if (!input.readFully(header, BLOCK)) break
            if (header.all { it == 0.toByte() }) break   // bloc de fin d'archive

            val name = cString(header, 0, 100)
            val prefix = cString(header, 345, 155)
            val size = cString(header, 124, 12).trim().toLongOrNull(8) ?: 0L
            val type = header[156].toInt().toChar()

            val data = ByteArray(size.toInt())
            if (size > 0 && !input.readFully(data, data.size)) break
            val padding = ((BLOCK - size % BLOCK) % BLOCK).toInt()
            if (padding > 0) input.skipFully(padding)

            when (type) {
                'L' -> longName = cString(data, 0, data.size)
                'x', 'g' -> Unit                              // pax : ignoré
                '0', '\u0000' -> {
                    val full = longName ?: if (prefix.isNotEmpty()) "$prefix/$name" else name
                    longName = null
                    yield(full to data)
                }
                else -> longName = null                       // dossiers, liens...
            }
        }
    }

    private fun cString(b: ByteArray, off: Int, len: Int): String {
        var end = off
        while (end < off + len && end < b.size && b[end] != 0.toByte()) end++
        return String(b, off, end - off, Charsets.ISO_8859_1)
    }

    private fun InputStream.readFully(buf: ByteArray, len: Int): Boolean {
        var read = 0
        while (read < len) {
            val n = read(buf, read, len - read)
            if (n < 0) return false
            read += n
        }
        return true
    }

    private fun InputStream.skipFully(count: Int) {
        var left = count.toLong()
        while (left > 0) {
            val n = skip(left)
            if (n <= 0) { if (read() < 0) return else left-- } else left -= n
        }
    }
}