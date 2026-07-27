package com.asterplay.tv.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.asterplay.tv.net.Channel
import java.text.Normalizer
import java.util.Locale

class ChannelDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
        db.execSQL("PRAGMA synchronous=NORMAL")
        db.execSQL("PRAGMA temp_store=MEMORY")
    }

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
            CREATE TABLE categories (
              type TEXT NOT NULL,
              name TEXT NOT NULL,
              cnt  INTEGER NOT NULL DEFAULT 0,
              PRIMARY KEY(type, name)
            )
            """.trimIndent()
        )
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
        db.execSQL("DROP TABLE IF EXISTS categories")
        db.execSQL("DROP TABLE IF EXISTS meta")
        onCreate(db)
    }

    fun clearAll() {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            db.execSQL("DELETE FROM channels")
            db.execSQL("DELETE FROM categories")
            db.execSQL("DELETE FROM meta")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setMeta(key: String, value: String) {
        val db = writableDatabase
        val cv = ContentValues().apply { put("k", key); put("v", value) }
        db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getMeta(key: String): String? {
        readableDatabase.rawQuery("SELECT v FROM meta WHERE k=?", arrayOf(key)).use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    fun currentUrl(): String? = getMeta("url")
    fun currentCount(): Int = getMeta("count")?.toIntOrNull() ?: 0
    fun sourceFile(): String? = getMeta("file")

    /**
     * Parses the playlist in a single pass:
     *  - Live channels are stored fully in [channels].
     *  - VOD and Series entries only aggregate group counts in [categories]
     *    (their rows are loaded on-demand from the cached raw file).
     */
    fun replaceAll(
        url: String,
        sourceFile: String?,
        channels: Sequence<Channel>,
        onProgress: ((Int) -> Unit)? = null
    ): Int {
        val db = writableDatabase
        var scanned = 0
        val vodCounts = HashMap<String, Int>()
        val serCounts = HashMap<String, Int>()

        db.beginTransactionNonExclusive()
        try {
            db.execSQL("DELETE FROM channels")
            db.execSQL("DELETE FROM categories")
            db.execSQL("DELETE FROM meta")

            val ins = db.compileStatement(
                "INSERT INTO channels(name,url,logo,grp,tvg,type) VALUES(?,?,?,?,?,'live')"
            )
            try {
                for (c in channels) {
                    scanned++
                    when (classify(c)) {
                        "live" -> {
                            ins.clearBindings()
                            ins.bindString(1, c.name)
                            ins.bindString(2, c.url)
                            if (c.logo != null) ins.bindString(3, c.logo) else ins.bindNull(3)
                            if (c.group != null) ins.bindString(4, c.group) else ins.bindNull(4)
                            if (c.tvgId != null) ins.bindString(5, c.tvgId) else ins.bindNull(5)
                            ins.executeInsert()
                        }
                        "vod" -> {
                            val g = c.group?.trim().takeUnless { it.isNullOrEmpty() } ?: "Outros"
                            vodCounts[g] = (vodCounts[g] ?: 0) + 1
                        }
                        "series" -> {
                            val g = c.group?.trim().takeUnless { it.isNullOrEmpty() } ?: "Outros"
                            serCounts[g] = (serCounts[g] ?: 0) + 1
                        }
                    }
                    if (scanned % 5_000 == 0) onProgress?.invoke(scanned)
                }
            } finally {
                ins.close()
            }

            val insCat = db.compileStatement(
                "INSERT INTO categories(type,name,cnt) VALUES(?,?,?)"
            )
            try {
                for ((g, n) in vodCounts) {
                    insCat.clearBindings()
                    insCat.bindString(1, "vod")
                    insCat.bindString(2, g)
                    insCat.bindLong(3, n.toLong())
                    insCat.executeInsert()
                }
                for ((g, n) in serCounts) {
                    insCat.clearBindings()
                    insCat.bindString(1, "series")
                    insCat.bindString(2, g)
                    insCat.bindLong(3, n.toLong())
                    insCat.executeInsert()
                }
            } finally {
                insCat.close()
            }

            if (scanned > 0) {
                fun put(k: String, v: String) {
                    val cv = ContentValues().apply { put("k", k); put("v", v) }
                    db.insertWithOnConflict("meta", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
                }
                put("url", url)
                put("ts", System.currentTimeMillis().toString())
                put("count", scanned.toString())
                if (sourceFile != null) put("file", sourceFile)
                onProgress?.invoke(scanned)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return scanned
    }

    fun groupsByType(type: String): List<Pair<String, Int>> {
        val out = mutableListOf<Pair<String, Int>>()
        if (type == "live") {
            readableDatabase.rawQuery(
                "SELECT COALESCE(NULLIF(TRIM(grp),''),'Outros') AS g, COUNT(*) FROM channels WHERE type='live' GROUP BY g ORDER BY g COLLATE NOCASE",
                null
            ).use {
                while (it.moveToNext()) out += it.getString(0) to it.getInt(1)
            }
        } else {
            readableDatabase.rawQuery(
                "SELECT name, cnt FROM categories WHERE type=? ORDER BY name COLLATE NOCASE",
                arrayOf(type)
            ).use {
                while (it.moveToNext()) out += it.getString(0) to it.getInt(1)
            }
        }
        return out
    }

    fun channelsByGroup(type: String, group: String, limit: Int = 500): List<Channel> {
        if (type != "live") return emptyList()
        val out = ArrayList<Channel>()
        val sql = if (group == "Outros")
            "SELECT name,url,logo,grp,tvg FROM channels WHERE type='live' AND (grp=? OR grp IS NULL OR TRIM(grp)='') ORDER BY name COLLATE NOCASE LIMIT ?"
        else
            "SELECT name,url,logo,grp,tvg FROM channels WHERE type='live' AND grp=? ORDER BY name COLLATE NOCASE LIMIT ?"
        readableDatabase.rawQuery(sql, arrayOf(group, limit.toString())).use {
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
        private const val DB_VERSION = 4

        private val DIACRITICS = "\\p{Mn}+".toRegex()
        private val SEPARATORS = "[_\\-]+".toRegex()
        private val SERIES_REGEX = termsRegex(
            "serie", "series", "seriado", "seriados", "temporada", "temporadas",
            "episodio", "episodios", "capitulo", "capitulos", "novela", "novelas"
        )
        private val LIVE_REGEX = termsRegex(
            "canal", "canais", "ao vivo", "tv", "televisao", "esporte ao vivo",
            "jornal", "noticias", "infantil ao vivo"
        )
        private val MOVIE_REGEX = termsRegex(
            "filme", "filmes", "movie", "movies", "cinema", "vod", "lancamento",
            "lancamentos", "dublado", "legendado", "acao", "comedia", "terror"
        )

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
            val u = c.url.lowercase(Locale.ROOT)

            if (u.contains("/series/") || u.contains("/serie/")) return "series"
            if (u.contains("/movie/") || u.contains("/movies/") || u.contains("/vod/")) return "vod"
            if (u.contains("/live/")) return "live"

            if (isLiveGroup(group)) return "live"

            if (SERIES_REGEX.containsMatchIn(source)) return "series"
            if (LIVE_REGEX.containsMatchIn(source)) return "live"
            if (MOVIE_REGEX.containsMatchIn(source)) return "vod"

            if (u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi") || u.endsWith(".mov") || u.endsWith(".m4v")) return "vod"
            return "live"
        }

        private fun normalize(value: String): String {
            if (value.isEmpty()) return ""
            val noAccent = if (value.any { it.code > 127 }) {
                Normalizer.normalize(value, Normalizer.Form.NFD).replace(DIACRITICS, "")
            } else {
                value
            }
            return noAccent
                .lowercase(Locale.ROOT)
                .replace(SEPARATORS, " ")
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

        private fun termsRegex(vararg terms: String): Regex {
            val body = terms.joinToString("|") { Regex.escape(it) }
            return Regex("(^|[^a-z0-9])(?:$body)([^a-z0-9]|$)")
        }
    }
}
