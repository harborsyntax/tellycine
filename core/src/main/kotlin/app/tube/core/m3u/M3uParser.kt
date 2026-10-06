package app.tube.core.m3u

import java.io.BufferedReader

data class M3uHeader(
    /** EPG URLs advertised via `url-tvg` / `x-tvg-url` (may be comma separated in the source). */
    val epgUrls: List<String>,
)

data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String?,
    val tvgName: String?,
    val logo: String?,
    val group: String?,
    val userAgent: String?,
    val referrer: String?,
    val catchupDays: Int?,
    val attributes: Map<String, String>,
)

/**
 * Streaming M3U / M3U8 (extended) playlist parser. Entries are emitted one at a time
 * through a callback so arbitrarily large playlists can be processed in constant memory.
 */
class M3uParser {

    /** @return number of entries emitted */
    fun parse(
        reader: BufferedReader,
        onHeader: (M3uHeader) -> Unit = {},
        onEntry: (M3uEntry) -> Unit,
    ): Int {
        var count = 0
        var pendingInfo: String? = null
        var pendingGroup: String? = null
        var pendingUserAgent: String? = null
        var pendingReferrer: String? = null
        var first = true

        while (true) {
            var line = reader.readLine() ?: break
            if (first) {
                line = line.removePrefix("﻿")
                first = false
            }
            line = line.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> {
                    val attrs = parseAttributes(line.substring(7))
                    val urls = listOfNotNull(attrs["url-tvg"], attrs["x-tvg-url"], attrs["tvg-url"])
                        .flatMap { it.split(',') }
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()
                    onHeader(M3uHeader(urls))
                }

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pendingInfo = line
                }

                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    pendingGroup = line.substring(8).trim().ifEmpty { null }
                }

                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substring(11).trim()
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> pendingUserAgent = value.ifEmpty { null }
                        "http-referrer", "http-referer" -> pendingReferrer = value.ifEmpty { null }
                    }
                }

                line.startsWith("#") -> Unit // other directives (#KODIPROP, #EXTBYT, comments)

                else -> {
                    val info = pendingInfo
                    val entry = buildEntry(info, line, pendingGroup, pendingUserAgent, pendingReferrer)
                    if (entry != null) {
                        onEntry(entry)
                        count++
                    }
                    pendingInfo = null
                    pendingGroup = null
                    pendingUserAgent = null
                    pendingReferrer = null
                }
            }
        }
        return count
    }

    private fun buildEntry(
        info: String?,
        rawUrl: String,
        extGroup: String?,
        vlcUserAgent: String?,
        vlcReferrer: String?,
    ): M3uEntry? {
        // Some playlists append Kodi-style headers to the URL: http://host/stream|User-Agent=foo
        var url = rawUrl
        var userAgent = vlcUserAgent
        var referrer = vlcReferrer
        val pipe = rawUrl.indexOf('|')
        if (pipe > 0) {
            url = rawUrl.substring(0, pipe)
            rawUrl.substring(pipe + 1).split('&').forEach { part ->
                val k = part.substringBefore('=').trim().lowercase()
                val v = part.substringAfter('=', "").trim()
                if (k == "user-agent" && v.isNotEmpty()) userAgent = v
                if ((k == "referer" || k == "referrer") && v.isNotEmpty()) referrer = v
            }
        }
        if (url.isBlank()) return null

        if (info == null) {
            return M3uEntry(
                name = url.substringAfterLast('/').ifEmpty { url },
                url = url,
                tvgId = null, tvgName = null, logo = null,
                group = extGroup,
                userAgent = userAgent, referrer = referrer,
                catchupDays = null,
                attributes = emptyMap(),
            )
        }

        // "#EXTINF:-1 key="value" key2="value, with comma",Display Name"
        val body = info.substringAfter(':', "")
        val commaIndex = findTitleComma(body)
        val attrPart = if (commaIndex >= 0) body.substring(0, commaIndex) else body
        val title = if (commaIndex >= 0) body.substring(commaIndex + 1).trim() else ""
        val attrs = parseAttributes(attrPart)

        val tvgName = attrs["tvg-name"]?.ifBlank { null }
        val name = title.ifEmpty { tvgName ?: url.substringAfterLast('/') }

        return M3uEntry(
            name = name,
            url = url,
            tvgId = attrs["tvg-id"]?.ifBlank { null },
            tvgName = tvgName,
            logo = (attrs["tvg-logo"] ?: attrs["logo"])?.ifBlank { null },
            group = attrs["group-title"]?.ifBlank { null } ?: extGroup,
            userAgent = attrs["user-agent"]?.ifBlank { null } ?: userAgent,
            referrer = (attrs["referrer"] ?: attrs["http-referrer"])?.ifBlank { null } ?: referrer,
            catchupDays = (attrs["catchup-days"] ?: attrs["tvg-rec"])?.toIntOrNull(),
            attributes = attrs,
        )
    }

    /** Finds the comma separating attributes from the title, ignoring commas inside quotes. */
    private fun findTitleComma(s: String): Int {
        var quote: Char? = null
        for (i in s.indices) {
            val c = s[i]
            when {
                quote != null -> if (c == quote) quote = null
                c == '"' -> quote = c
                c == ',' -> return i
            }
        }
        return -1
    }

    companion object {
        /** Parses `key="value"` (or `key='value'` / `key=value`) pairs. Keys are lower-cased. */
        fun parseAttributes(s: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            var i = 0
            val n = s.length
            while (i < n) {
                while (i < n && (s[i].isWhitespace())) i++
                val keyStart = i
                while (i < n && s[i] != '=' && !s[i].isWhitespace()) i++
                val key = s.substring(keyStart, i)
                if (i >= n || s[i] != '=') {
                    // bare token such as the "-1" duration; skip it
                    if (i == keyStart) i++
                    continue
                }
                i++ // '='
                val value: String
                if (i < n && (s[i] == '"' || s[i] == '\'')) {
                    val q = s[i]
                    val end = s.indexOf(q, i + 1).let { if (it < 0) n else it }
                    value = s.substring(i + 1, end)
                    i = end + 1
                } else {
                    val start = i
                    while (i < n && !s[i].isWhitespace()) i++
                    value = s.substring(start, i)
                }
                if (key.isNotEmpty()) result[key.lowercase()] = value.trim()
            }
            return result
        }
    }
}
