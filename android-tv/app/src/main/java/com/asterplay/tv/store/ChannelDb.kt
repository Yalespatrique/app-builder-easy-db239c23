package com.asterplay.tv.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.asterplay.tv.net.Channel
import java.text.Normalizer

class ChannelDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE channels (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              name TEXT NOT NULL,
              url  TEXT NOT NULL,
              logo TEXT,
              grp  TEXT,
              tvg  TEXT,
              type TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_type ON channels(type)")
        db.execSQL("CREATE INDEX idx_type_grp ON channels(type, grp)")
        db.execSQL("CREATE INDEX idx_name ON channels(name)")
        db.execSQL(
            """
            CREATE TABLE meta (
              k TEXT PRIMARY KEY,
              v TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS channels")
        db.execSQL("DROP TABLE IF EXISTS meta")
        onCreate(db)
    }

    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM channels")
        writableDatabase.execSQL("DELETE FROM meta")
    }

    fun setMeta(url: String, count: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            fun put(k: String, v: String) {
                val cv = ContentValues().apply { put("k", k); put("v", v) }
                db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            put("url", url)
            put("ts", System.currentTimeMillis().toString())
            put("count", count.toString())
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getMeta(key: String): String? {
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(key)).use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    fun currentUrl(): String? = getMeta("url")
    fun currentCount(): Int = getMeta("count")?.toIntOrNull() ?: 0

    fun bulkInsert(channels: Iterable<Pair<Channel, String>>): Int {
        val db = writableDatabase
        var count = 0
        db.beginTransactionNonExclusive()
        try {
            val stmt = db.compileStatement(
                "INSERT INTO channels(name,url,logo,grp,tvg,type) VALUES(?,?,?,?,?,?)"
            )
            for ((c, type) in channels) {
                stmt.clearBindings()
                stmt.bindString(1, c.name)
                stmt.bindString(2, c.url)
                if (c.logo != null) stmt.bindString(3, c.logo) else stmt.bindNull(3)
                if (c.group != null) stmt.bindString(4, c.group) else stmt.bindNull(4)
                if (c.tvgId != null) stmt.bindString(5, c.tvgId) else stmt.bindNull(5)
                stmt.bindString(6, type)
                stmt.executeInsert()
                count++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return count
    }

    fun groupsByType(type: String): List<Pair<String, Int>> {
        val out = mutableListOf<Pair<String, Int>>()
        readableDatabase.rawQuery(
            "SELECT COALESCE(NULLIF(TRIM(grp),''),'Outros') AS g, COUNT(*) FROM channels WHERE type=? GROUP BY g ORDER BY g COLLATE NOCASE",
            arrayOf(type)
        ).use {
            while (it.moveToNext()) out += it.getString(0) to it.getInt(1)
        }
        return out
    }

    fun channelsByGroup(type: String, group: String, limit: Int = 500): List<Channel> {
        val out = ArrayList<Channel>()
        val sql = if (group == "Outros")
            "SELECT name,url,logo,grp,tvg FROM channels WHERE type=? AND (grp=? OR grp IS NULL OR TRIM(grp)='') ORDER BY name COLLATE NOCASE LIMIT ?"
        else
            "SELECT name,url,logo,grp,tvg FROM channels WHERE type=? AND grp=? ORDER BY name COLLATE NOCASE LIMIT ?"
        val args = if (group == "Outros") arrayOf(type, group, limit.toString())
                   else arrayOf(type, group, limit.toString())
        readableDatabase.rawQuery(sql, args).use {
            while (it.moveToNext()) {
                out += Channel(
                    name = it.getString(0),
                    url = it.getString(1),
                    logo = it.getString(2),
                    group = it.getString(3),
                    tvgId = it.getString(4),
                )
            }
        }
        return out
    }

    fun search(query: String, limit: Int = 200): List<Channel> {
        val out = ArrayList<Channel>()
        readableDatabase.rawQuery(
            "SELECT name,url,logo,grp,tvg FROM channels WHERE name LIKE ? ORDER BY name COLLATE NOCASE LIMIT ?",
            arrayOf("%${query}%", limit.toString())
        ).use {
            while (it.moveToNext()) {
                out += Channel(
                    name = it.getString(0),
                    url = it.getString(1),
                    logo = it.getString(2),
                    group = it.getString(3),
                    tvgId = it.getString(4),
                )
            }
        }
        return out
    }

    companion object {
        private const val DB_NAME = "asterplay.db"
        private const val DB_VERSION = 2

        @Volatile private var INSTANCE: ChannelDb? = null
        fun get(ctx: Context): ChannelDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChannelDb(ctx).also { INSTANCE = it }
            }
        }

        fun classify(c: Channel): String {
            val group = normalize(c.group.orEmpty())
            val name = normalize(c.name)
            val source = "$group $name"
            val u = c.url.lowercase()

            if (u.contains("/series/") || u.contains("/serie/")) return "series"
            if (u.contains("/movie/") || u.contains("/movies/") || u.contains("/vod/")) return "vod"
            if (u.contains("/live/")) return "live"

            if (isLiveGroup(group)) return "live"

            val isSeries = hasAny(source, listOf("serie", "series", "seriado", "seriados", "temporada", "temporadas", "episodio", "episodios", "capitulo", "capitulos", "novela", "novelas"))
            if (isSeries) return "series"

            val isLive = hasAny(source, listOf("canal", "canais", "ao vivo", "tv", "televisao", "esporte ao vivo", "jornal", "noticias", "infantil ao vivo"))
            if (isLive) return "live"

            val isMovie = hasAny(source, listOf("filme", "filmes", "movie", "movies", "cinema", "vod", "lancamento", "lancamentos", "dublado", "legendado", "acao", "comedia", "terror"))
            if (isMovie) return "vod"

            if (u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi") || u.endsWith(".mov") || u.endsWith(".m4v")) return "vod"
            return "live"
        }

        private fun normalize(value: String): String {
            return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
                .lowercase()
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
        }

        private fun isLiveGroup(group: String): Boolean {
            return group.startsWith("canais") ||
                group.startsWith("canal ") ||
                group.startsWith("ao vivo") ||
                group.startsWith("tv ") ||
                group.contains("|| canais") ||
                group.contains("| canais")
        }

        private fun hasAny(text: String, terms: List<String>): Boolean {
            return terms.any { term ->
                Regex("(^|[^a-z0-9])${Regex.escape(term)}([^a-z0-9]|$)").containsMatchIn(text)
            }
        }
    }
}
