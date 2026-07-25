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
        val out = mutableListOf<Channel>()
        val lines = text.lineSequence().iterator()
        var pending: Map<String, String>? = null
        var pendingName: String? = null
        while (lines.hasNext()) {
            val line = lines.next().trim()
            if (line.startsWith("#EXTINF")) {
                val attrs = attrRegex.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                pending = attrs
                pendingName = line.substringAfterLast(",").trim()
            } else if (line.isNotEmpty() && !line.startsWith("#") && pending != null) {
                out += Channel(
                    name = pendingName ?: "Sem nome",
                    url = line,
                    logo = pending["tvg-logo"],
                    group = pending["group-title"],
                    tvgId = pending["tvg-id"]
                )
                pending = null
                pendingName = null
            }
        }
        return out
    }
}
