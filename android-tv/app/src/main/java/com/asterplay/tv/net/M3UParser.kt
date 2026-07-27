package com.asterplay.tv.net

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String?,
    val tvgId: String?
)

object M3UParser {
    fun parse(text: String): List<Channel> {
        return parseLines(text.lineSequence())
    }

    fun parseLines(lines: Sequence<String>): List<Channel> {
        val out = mutableListOf<Channel>()
        forEach(lines) { out += it }
        return out
    }

    fun parseSequence(lines: Sequence<String>): Sequence<Channel> = sequence {
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingTvgId: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                pendingLogo = readAttr(line, "tvg-logo")
                pendingGroup = readAttr(line, "group-title")
                pendingTvgId = readAttr(line, "tvg-id")
                pendingName = line.substringAfterLast(",", "Sem nome").trim().ifEmpty { "Sem nome" }
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val name = pendingName ?: continue
                yield(
                    Channel(
                        name = name,
                        url = line,
                        logo = pendingLogo,
                        group = pendingGroup,
                        tvgId = pendingTvgId
                    )
                )
                pendingName = null
                pendingLogo = null
                pendingGroup = null
                pendingTvgId = null
            }
        }
    }

    fun forEach(lines: Sequence<String>, onChannel: (Channel) -> Unit) {
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null
        var pendingTvgId: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                pendingLogo = readAttr(line, "tvg-logo")
                pendingGroup = readAttr(line, "group-title")
                pendingTvgId = readAttr(line, "tvg-id")
                pendingName = line.substringAfterLast(",", "Sem nome").trim().ifEmpty { "Sem nome" }
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val name = pendingName ?: continue
                onChannel(
                    Channel(
                        name = name,
                        url = line,
                        logo = pendingLogo,
                        group = pendingGroup,
                        tvgId = pendingTvgId
                    )
                )
                pendingName = null
                pendingLogo = null
                pendingGroup = null
                pendingTvgId = null
            }
        }
    }

    private fun readAttr(line: String, key: String): String? {
        val marker = "$key=\""
        val start = line.indexOf(marker)
        if (start < 0) return null
        val valueStart = start + marker.length
        val valueEnd = line.indexOf('"', valueStart)
        if (valueEnd <= valueStart) return null
        return line.substring(valueStart, valueEnd)
    }
}
