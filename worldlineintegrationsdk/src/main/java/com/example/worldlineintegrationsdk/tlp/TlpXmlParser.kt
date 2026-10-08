package com.example.worldlineintegrationsdk.tlp

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class ParsedField(val recordIndex: Int, val path: String, val value: String)

data class ParsedTable(
    val id: String,
    val tag: String,
    val version: String,
    val recordCount: Int,
    val fields: List<ParsedField>
)

/**
 * Parse un fichier de table TLP (urn:sacc:tlp).
 *
 * Deux formes sont gérées :
 *  - table plate : <parameters><field tag="21">978</field>...  -> recordIndex 0
 *  - table liste : <parameters><field><field tag=..>..</field></field>... -> un record par
 *    <field> sans tag
 * Les champs imbriqués avec tag (ex. 20012 dans table41) donnent un path "20012/10307".
 */
object TlpXmlParser {

    private class Frame(val tag: String?) {
        val text = StringBuilder()
        var hasChildren = false
    }

    fun parse(input: InputStream): ParsedTable {
        val parser = Xml.newPullParser()
        parser.setInput(input, "UTF-8")

        var id = ""
        var tag = ""
        var version = ""

        val fields = ArrayList<ParsedField>()
        val stack = ArrayList<Frame>()
        var wrapperCount = 0
        var currentRecord = 0
        var sawFlat = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "table" -> {
                        id = parser.getAttributeValue(null, "id") ?: ""
                        tag = parser.getAttributeValue(null, "tag") ?: ""
                        version = parser.getAttributeValue(null, "version") ?: ""
                    }
                    "field" -> {
                        val fieldTag = parser.getAttributeValue(null, "tag")
                        if (stack.isEmpty()) {
                            // enfant direct de <parameters>
                            if (fieldTag == null) {
                                currentRecord = wrapperCount++
                            } else {
                                currentRecord = 0
                                sawFlat = true
                            }
                        } else {
                            stack.last().hasChildren = true
                        }
                        stack.add(Frame(fieldTag))
                    }
                }

                XmlPullParser.TEXT -> {
                    if (stack.isNotEmpty()) stack.last().text.append(parser.text)
                }

                XmlPullParser.END_TAG -> if (parser.name == "field") {
                    val frame = stack.removeAt(stack.size - 1)
                    if (frame.tag != null && !frame.hasChildren) {
                        val path = buildString {
                            for (f in stack) f.tag?.let { append(it).append('/') }
                            append(frame.tag)
                        }
                        fields.add(
                            ParsedField(
                                recordIndex = currentRecord,
                                path = path,
                                value = frame.text.toString().trimEnd()
                            )
                        )
                    }
                }
            }
            event = parser.next()
        }

        val recordCount = if (wrapperCount > 0) wrapperCount else if (sawFlat) 1 else 0
        return ParsedTable(id, tag, version, recordCount, fields)
    }
}