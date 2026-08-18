package com.asterplay.tv.store

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.asterplay.tv.net.Channel
import com.asterplay.tv.net.XtreamCategory

/**
 * Cache "CDM": guarda só o que o usuário já acessou, com TTL.
 * Chaves são por (kind, host, username) para não misturar contas.
 */
class CacheDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE cat_cache(
              account TEXT NOT NULL,
              kind    TEXT NOT NULL,
              cat_id  TEXT NOT NULL,
              name    TEXT NOT NULL,
              pos     INTEGER NOT NULL,
              expires INTEGER NOT NULL,
              PRIMARY KEY(account, kind, cat_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE streams_cache(
              account   TEXT NOT NULL,
              kind      TEXT NOT NULL,
              cat_id    TEXT NOT NULL,
              stream_id TEXT NOT NULL,
              name      TEXT NOT NULL,
              url       TEXT NOT NULL,
              logo      TEXT,
              tvg       TEXT,
              pos       INTEGER NOT NULL,
              expires   INTEGER NOT NULL,
              PRIMARY KEY(account, kind, cat_id, stream_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_streams_cat ON streams_cache(account, kind, cat_id)")
        db.execSQL("CREATE INDEX idx_streams_name ON streams_cache(account, name)")
        db.execSQL(
            """
            CREATE TABLE cat_counts(
              account TEXT NOT NULL,
              kind    TEXT NOT NULL,
              cat_id  TEXT NOT NULL,
              total   INTEGER NOT NULL,
              PRIMARY KEY(account, kind, cat_id)
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS cat_cache")
        db.execSQL("DROP TABLE IF EXISTS streams_cache")
        db.execSQL("DROP TABLE IF EXISTS cat_counts")
        onCreate(db)
    }

    // -------- Categorias --------

    fun readCategories(account: String, kind: String): List<XtreamCategory>? {
        val now = System.currentTimeMillis()
        val out = ArrayList<XtreamCategory>()
        var anyValid = false
        readableDatabase.rawQuery(
            "SELECT cat_id, name, expires FROM cat_cache WHERE account=? AND kind=? ORDER BY pos ASC",
            arrayOf(account, kind),
        ).use {
            while (it.moveToNext()) {
                if (it.getLong(2) > now) anyValid = true
                out += XtreamCategory(it.getString(0), it.getString(1))
            }
        }
        return if (out.isNotEmpty() && anyValid) out else null
    }

    fun writeCategories(account: String, kind: String, list: List<XtreamCategory>, ttlMs: Long) {
        val db = writableDatabase
        val exp = System.currentTimeMillis() + ttlMs
        db.beginTransaction()
        try {
            db.delete("cat_cache", "account=? AND kind=?", arrayOf(account, kind))
            val ins = db.compileStatement("INSERT INTO cat_cache(account,kind,cat_id,name,pos,expires) VALUES(?,?,?,?,?,?)")
            list.forEachIndexed { i, c ->
                ins.clearBindings()
                ins.bindString(1, account); ins.bindString(2, kind)
                ins.bindString(3, c.id); ins.bindString(4, c.name)
                ins.bindLong(5, i.toLong()); ins.bindLong(6, exp)
                ins.executeInsert()
            }
            ins.close()
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    // -------- Streams --------

    fun readStreams(account: String, kind: String, catId: String): List<Channel>? {
        val now = System.currentTimeMillis()
        val out = ArrayList<Channel>()
        var anyValid = false
        readableDatabase.rawQuery(
            "SELECT name,url,logo,tvg,expires FROM streams_cache WHERE account=? AND kind=? AND cat_id=? ORDER BY pos ASC",
            arrayOf(account, kind, catId),
        ).use {
            while (it.moveToNext()) {
                if (it.getLong(4) > now) anyValid = true
                out += Channel(
                    name = it.getString(0),
                    url = it.getString(1),
                    logo = it.getString(2),
                    group = catId,
                    tvgId = it.getString(3),
                )
            }
        }
        return if (out.isNotEmpty() && anyValid) out else null
    }

    fun writeStreams(account: String, kind: String, catId: String, list: List<Channel>, ttlMs: Long) {
        val db = writableDatabase
        val exp = System.currentTimeMillis() + ttlMs
        db.beginTransaction()
        try {
            db.delete("streams_cache", "account=? AND kind=? AND cat_id=?", arrayOf(account, kind, catId))
            val ins = db.compileStatement(
                "INSERT INTO streams_cache(account,kind,cat_id,stream_id,name,url,logo,tvg,pos,expires) VALUES(?,?,?,?,?,?,?,?,?,?)"
            )
            list.forEachIndexed { i, c ->
                ins.clearBindings()
                ins.bindString(1, account); ins.bindString(2, kind); ins.bindString(3, catId)
                ins.bindString(4, c.url.substringAfterLast('/').substringBefore('.'))
                ins.bindString(5, c.name); ins.bindString(6, c.url)
                if (c.logo != null) ins.bindString(7, c.logo) else ins.bindNull(7)
                if (c.tvgId != null) ins.bindString(8, c.tvgId) else ins.bindNull(8)
                ins.bindLong(9, i.toLong()); ins.bindLong(10, exp)
                ins.executeInsert()
            }
            ins.close()
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /**
     * Totais por categoria. Usa primeiro o total real do provedor (cat_counts,
     * preenchido no preload) e, se não houver, cai no que já está cacheado.
     */
    fun countsByCategory(account: String, kind: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        readableDatabase.rawQuery(
            "SELECT cat_id, COUNT(*) FROM streams_cache WHERE account=? AND kind=? GROUP BY cat_id",
            arrayOf(account, kind),
        ).use {
            while (it.moveToNext()) out[it.getString(0)] = it.getInt(1)
        }
        out.putAll(readCounts(account, kind))
        return out
    }

    /** Totais oficiais (vindos do catálogo do provedor). */
    fun readCounts(account: String, kind: String): Map<String, Int> {
        val out = HashMap<String, Int>()
        readableDatabase.rawQuery(
            "SELECT cat_id, total FROM cat_counts WHERE account=? AND kind=?",
            arrayOf(account, kind),
        ).use {
            while (it.moveToNext()) out[it.getString(0)] = it.getInt(1)
        }
        return out
    }

    fun writeCounts(account: String, kind: String, counts: Map<String, Int>) {
        if (counts.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("cat_counts", "account=? AND kind=?", arrayOf(account, kind))
            val ins = db.compileStatement("INSERT INTO cat_counts(account,kind,cat_id,total) VALUES(?,?,?,?)")
            for ((catId, total) in counts) {
                ins.clearBindings()
                ins.bindString(1, account); ins.bindString(2, kind)
                ins.bindString(3, catId); ins.bindLong(4, total.toLong())
                ins.executeInsert()
            }
            ins.close()
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    // -------- Busca (só nos itens já cacheados) --------

    fun search(account: String, query: String, limit: Int = 200): List<Channel> {
        val out = ArrayList<Channel>()
        readableDatabase.rawQuery(
            "SELECT name,url,logo,cat_id,tvg FROM streams_cache WHERE account=? AND name LIKE ? ORDER BY name COLLATE NOCASE LIMIT ?",
            arrayOf(account, "%$query%", limit.toString()),
        ).use {
            while (it.moveToNext()) {
                out += Channel(
                    name = it.getString(0), url = it.getString(1), logo = it.getString(2),
                    group = it.getString(3), tvgId = it.getString(4),
                )
            }
        }
        return out
    }

    /** Busca restrita a um tipo (live / vod / series). */
    fun searchKind(account: String, kind: String, query: String, limit: Int = 300): List<Channel> {
        val out = ArrayList<Channel>()
        readableDatabase.rawQuery(
            "SELECT name,url,logo,cat_id,tvg FROM streams_cache WHERE account=? AND kind=? AND name LIKE ? ORDER BY name COLLATE NOCASE LIMIT ?",
            arrayOf(account, kind, "%$query%", limit.toString()),
        ).use {
            while (it.moveToNext()) {
                out += Channel(
                    name = it.getString(0), url = it.getString(1), logo = it.getString(2),
                    group = it.getString(3), tvgId = it.getString(4),
                )
            }
        }
        return out
    }

    /** Busca canais no cache pelas URLs (usado no Home para "Continuar assistindo"). */
    fun findByUrls(account: String, urls: List<String>): Map<String, Channel> {
        if (urls.isEmpty()) return emptyMap()
        val placeholders = urls.joinToString(",") { "?" }
        val args = ArrayList<String>(urls.size + 1).apply { add(account); addAll(urls) }
        val out = HashMap<String, Channel>()
        readableDatabase.rawQuery(
            "SELECT name,url,logo,cat_id,tvg FROM streams_cache WHERE account=? AND url IN ($placeholders)",
            args.toTypedArray(),
        ).use {
            while (it.moveToNext()) {
                out[it.getString(1)] = Channel(
                    name = it.getString(0), url = it.getString(1), logo = it.getString(2),
                    group = it.getString(3), tvgId = it.getString(4),
                )
            }
        }
        return out
    fun findChannelsByUrls(account: String, kind: String, urls: List<String>): List<Channel> {
        if (urls.isEmpty()) return emptyList()
        val placeholders = urls.joinToString(",") { "?" }
        val args = ArrayList<String>(urls.size + 2).apply { add(account); add(kind); addAll(urls) }
        val out = mutableListOf<Channel>()
        readableDatabase.rawQuery(
            "SELECT s.name, s.url, s.logo, s.cat_id, s.tvg FROM streams_cache s " +
            "JOIN cat_cache c ON s.cat_id = c.id AND s.account = c.account " +
            "WHERE s.account=? AND c.kind=? AND s.url IN ($placeholders)",
            args.toTypedArray(),
        ).use {
            while (it.moveToNext()) {
                out.add(Channel(
                    name = it.getString(0), url = it.getString(1), logo = it.getString(2),
                    group = it.getString(3), tvgId = it.getString(4),
                ))
            }
        }
        return out
    }


    /** Procura no cache o primeiro item cujo nome contenha [query] (case-insensitive). */
    fun findFirstByName(account: String, query: String): Channel? {
        readableDatabase.rawQuery(
            "SELECT name,url,logo,cat_id,tvg FROM streams_cache WHERE account=? AND name LIKE ? COLLATE NOCASE LIMIT 1",
            arrayOf(account, "%$query%"),
        ).use {
            if (it.moveToNext()) return Channel(
                name = it.getString(0), url = it.getString(1), logo = it.getString(2),
                group = it.getString(3), tvgId = it.getString(4),
            )
        }
        return null
    }

    fun clearAll() {
        writableDatabase.execSQL("DELETE FROM cat_cache")
        writableDatabase.execSQL("DELETE FROM streams_cache")
        writableDatabase.execSQL("DELETE FROM cat_counts")
    }

    companion object {
        private const val DB_NAME = "asterplay_cache.db"
        private const val DB_VERSION = 2

        // TTLs
        const val TTL_CATEGORIES = 24L * 3600 * 1000
        const val TTL_STREAMS    =  6L * 3600 * 1000

        @Volatile private var INSTANCE: CacheDb? = null
        fun get(ctx: Context): CacheDb =
            INSTANCE ?: synchronized(this) { INSTANCE ?: CacheDb(ctx).also { INSTANCE = it } }

        fun accountKey(host: String, user: String): String = "$host|$user"
    }
}
