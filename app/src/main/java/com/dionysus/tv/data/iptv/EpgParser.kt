package com.dionysus.tv.data.iptv

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.Calendar
import java.util.TimeZone

/**
 * Streaming XMLTV parser for EPG data. Pulls `<programme>` elements and groups
 * them by channel id. Best-effort: malformed timestamps are skipped rather than
 * failing the whole guide, and the total is capped so a huge national EPG can't
 * exhaust memory on a low-end TV box.
 */
object EpgParser {

    private const val MAX_PROGRAMMES = 60_000

    fun parse(input: InputStream): Map<String, List<Programme>> {
        val byChannel = HashMap<String, MutableList<Programme>>()
        var count = 0
        runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT && count < MAX_PROGRAMMES) {
                if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                    val channel = parser.getAttributeValue(null, "channel").orEmpty()
                    val start = parseTime(parser.getAttributeValue(null, "start"))
                    val stop = parseTime(parser.getAttributeValue(null, "stop"))
                    var title = ""
                    var desc = ""
                    // Walk the children of this <programme>.
                    var inner = parser.next()
                    while (!(inner == XmlPullParser.END_TAG && parser.name == "programme")) {
                        if (inner == XmlPullParser.START_TAG) {
                            when (parser.name) {
                                "title" -> title = parser.nextText().trim()
                                "desc" -> desc = parser.nextText().trim()
                            }
                        }
                        if (inner == XmlPullParser.END_DOCUMENT) break
                        inner = parser.next()
                    }
                    if (channel.isNotBlank() && start > 0 && title.isNotBlank()) {
                        byChannel.getOrPut(channel) { ArrayList() }
                            .add(Programme(channel, start, if (stop > 0) stop else start, title, desc))
                        count++
                    }
                }
                event = parser.next()
            }
        }
        byChannel.values.forEach { it.sortBy { p -> p.startMs } }
        return byChannel
    }

    /** XMLTV time is `yyyyMMddHHmmss` optionally followed by ` +ZZZZ`. */
    private fun parseTime(value: String?): Long {
        if (value.isNullOrBlank() || value.length < 14) return 0
        return runCatching {
            val digits = value.take(14)
            val year = digits.substring(0, 4).toInt()
            val month = digits.substring(4, 6).toInt()
            val day = digits.substring(6, 8).toInt()
            val hour = digits.substring(8, 10).toInt()
            val min = digits.substring(10, 12).toInt()
            val sec = digits.substring(12, 14).toInt()
            val tz = value.drop(14).trim().takeIf { it.isNotEmpty() }
            val zone = if (tz != null) TimeZone.getTimeZone("GMT$tz") else TimeZone.getTimeZone("UTC")
            Calendar.getInstance(zone).apply {
                clear()
                set(year, month - 1, day, hour, min, sec)
            }.timeInMillis
        }.getOrDefault(0L)
    }
}
