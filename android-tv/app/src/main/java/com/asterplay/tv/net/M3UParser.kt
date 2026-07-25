package com.asterplay.tv.net

data class Channel(
    val name: String,
    val url: String,
    val logo: String?,
    val group: String?,
    val tvgId: String?
)

object M3UParser {
    private val attrRegex = Regex("""(\S+?)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        return parseLines(text.lineSequence())
    }

    fun parseLines(lines: Sequence<String>): List<Channel> {
        val out = mutableListOf<Channel>()
        forEach(lines) { out += it }
        return out
    }

    fun forEach(lines: Sequence<String>, onChannel: (Channel) -> Unit) {
        var pending: Map<String, String>? = null
        var pendingName: String? = null
        for (raw in lines) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                val attrs = attrRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                pending = attrs
                pendingName = line.substringAfterLast(",").trim()
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val attrs = pending ?: continue
                onChannel(
                    Channel(
                        name = pendingName ?: "Sem nome",
                        url = line,
                        logo = attrs["tvg-logo"],
                        group = attrs["group-title"],
                        tvgId = attrs["tvg-id"]
                    )
                )
                pending = null
                pendingName = null
            }
        }
    }
}
