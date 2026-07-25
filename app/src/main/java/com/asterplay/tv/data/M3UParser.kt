package com.asterplay.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class Channel(
    val name: String,
    val url: String,
    val group: String = "",
    val logo: String = "",
    val tvgId: String = "",
)

object M3UParser {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun download(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@withContext emptyList()
            parse(res.body?.string().orEmpty())
        }
    }

    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        val lines = text.lines()
        var pending: PartialChannel? = null
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) {
                pending = parseExtInf(line)
            } else if (!line.startsWith("#") && pending != null) {
                out.add(
                    Channel(
                        name = pending.name,
                        url = line,
                        group = pending.group,
                        logo = pending.logo,
                        tvgId = pending.tvgId,
                    )
                )
                pending = null
            }
        }
        return out
    }

    private data class PartialChannel(
        val name: String,
        val group: String,
        val logo: String,
        val tvgId: String,
    )

    private fun parseExtInf(line: String): PartialChannel {
        // #EXTINF:-1 tvg-id="a" tvg-logo="b" group-title="c",Nome do canal
        val commaIdx = line.indexOf(',')
        val name = if (commaIdx >= 0) line.substring(commaIdx + 1).trim() else "Sem nome"
        val head = if (commaIdx >= 0) line.substring(0, commaIdx) else line
        val attrs = Regex("""(\w[\w-]*)="([^"]*)"""").findAll(head).associate { it.groupValues[1] to it.groupValues[2] }
        return PartialChannel(
            name = name,
            group = attrs["group-title"].orEmpty(),
            logo = attrs["tvg-logo"].orEmpty(),
            tvgId = attrs["tvg-id"].orEmpty(),
        )
    }
}
