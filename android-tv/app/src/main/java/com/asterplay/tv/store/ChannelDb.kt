package com.asterplay.tv.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.asterplay.tv.net.Channel

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
            "SELECT name,url,logo,grp,tvg FROM channels WHERE type=? AND (grp IS NULL OR TRIM(grp)='') ORDER BY name COLLATE NOCASE LIMIT ?"
        else
            "SELECT name,url,logo,grp,tvg FROM channels WHERE type=? AND grp=? ORDER BY name COLLATE NOCASE LIMIT ?"
        val args = if (group == "Outros") arrayOf(type, limit.toString())
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
        private const val DB_VERSION = 1

        @Volatile private var INSTANCE: ChannelDb? = null
        fun get(ctx: Context): ChannelDb {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChannelDb(ctx).also { INSTANCE = it }
            }
        }

        fun classify(c: Channel): String {
            val g = c.group?.lowercase().orEmpty()
            val u = c.url.lowercase()
            val isSeries = g.contains("serie") || g.contains("série") || g.contains("temporada") || g.contains("novela") || u.contains("/series/")
            if (isSeries) return "series"
            val isMovie = g.contains("filme") || g.contains("movie") || g.contains("vod") || g.contains("cinema") ||
                    u.contains("/movie/") || u.endsWith(".mp4") || u.endsWith(".mkv") || u.endsWith(".avi")
            if (isMovie) return "vod"
            return "live"
        }
    }
}
