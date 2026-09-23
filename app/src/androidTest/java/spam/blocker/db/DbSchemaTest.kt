package spam.blocker.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import spam.blocker.util.Util

class DbSchemaTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val productionHelper = Db.getInstance(ctx)

    @Before
    fun setup() {
        // Schema tests should not create or modify Android notification channels.
        mockkObject(Util)
        every { Util.isFreshInstall(any()) } returns false
    }

    @After
    fun cleanup() {
        unmockkObject(Util)
        ctx.deleteDatabase(FRESH_DB_NAME)
        ctx.deleteDatabase(UPGRADE_DB_NAME)
    }

    @Test
    fun freshInstallAndUpgrade_matchAndSatisfyTableContracts() {
        withFreshDatabase { freshDb ->
            withV420DatabaseUpgradedToCurrent { upgradedDb ->
                assertEquals(Db.DB_VERSION, freshDb.version)
                assertEquals(Db.DB_VERSION, upgradedDb.version)

                val expectedContracts = currentTableContracts()

                expectedContracts.keys.forEach { table ->
                    val freshCols = tableColumns(freshDb, table)
                    val upgradedCols = tableColumns(upgradedDb, table)
                    val contractCols = expectedContracts[table] ?: emptySet()

                    val missingInFresh = upgradedCols - freshCols
                    val missingInUpgraded = freshCols - upgradedCols
                    assertTrue(
                        "Schema mismatch for table '$table': " +
                            "missing in fresh = $missingInFresh, missing in upgraded = $missingInUpgraded",
                        missingInFresh.isEmpty() && missingInUpgraded.isEmpty()
                    )

                    val missingInAdapter = freshCols - contractCols
                    val extraInAdapter = contractCols - freshCols
                    assertTrue(
                        "Table adapter contract mismatch for table '$table': " +
                            "missing in adapter = $missingInAdapter, extra in adapter = $extraInAdapter",
                        missingInAdapter.isEmpty() && extraInAdapter.isEmpty()
                    )
                }
            }
        }
    }

    /**
     * Exercise SQLiteOpenHelper itself instead of invoking Db callbacks directly. This keeps the
     * version detection and upgrade transaction lifecycle the same as the real app.
     */
    private fun withFreshDatabase(block: (SQLiteDatabase) -> Unit) {
        ctx.deleteDatabase(FRESH_DB_NAME)
        val helper = DelegatingDbHelper(ctx, FRESH_DB_NAME, productionHelper)
        try {
            block(helper.writableDatabase)
        } finally {
            helper.close()
            ctx.deleteDatabase(FRESH_DB_NAME)
        }
    }

    private fun withV420DatabaseUpgradedToCurrent(block: (SQLiteDatabase) -> Unit) {
        ctx.deleteDatabase(UPGRADE_DB_NAME)

        val oldDb = ctx.openOrCreateDatabase(UPGRADE_DB_NAME, Context.MODE_PRIVATE, null)
        try {
            createV420Schema(oldDb)
            oldDb.version = V420_DB_VERSION
        } finally {
            oldDb.close()
        }

        val helper = DelegatingDbHelper(ctx, UPGRADE_DB_NAME, productionHelper)
        try {
            block(helper.writableDatabase)
        } finally {
            helper.close()
            ctx.deleteDatabase(UPGRADE_DB_NAME)
        }
    }

    private fun currentTableContracts(): Map<String, Set<String>> {
        fun android.content.ContentValues.columns(): Set<String> = keySet().toSet()

        return mapOf(
            Db.TABLE_NUMBER_RULE to NumberRegexTable().toContentValues(RegexRule(), true).columns(),
            Db.TABLE_CONTENT_RULE to ContentRegexTable().toContentValues(RegexRule(), true).columns(),
            Db.TABLE_QUICK_COPY_RULE to QuickCopyRegexTable().toContentValues(RegexRule(), true).columns(),
            Db.TABLE_NOTIFICATION_CHANNEL to Notification.ChannelTable
                .toContentValues(Notification.Channel(), true).columns(),
            Db.TABLE_SPAM to SpamTable.toContentValues(SpamNumber(), true).columns(),
            Db.TABLE_PUSH_ALERT to PushAlertTable.toContentValues(PushAlertRecord(), true).columns(),
            Db.TABLE_API_QUERY to QueryApiTable().toContentValues(QueryApi(), true).columns(),
            Db.TABLE_API_REPORT to ReportApiTable().toContentValues(ReportApi(), true).columns(),
            Db.TABLE_BOT to BotTable.toContentValues(Bot(), true).columns(),
            Db.TABLE_CALL to CallTable().toContentValues(HistoryRecord(), true).columns(),
            Db.TABLE_SMS to SmsTable().toContentValues(HistoryRecord(), true).columns(),
            Db.TABLE_BAYESIAN_FILTER to BayesTable.toContentValues(
                BayesSample(isSpam = false, content = "", hash = 0),
                true
            ).columns(),
        )
    }

    private fun tableColumns(db: SQLiteDatabase, table: String): Set<String> {
        val columns = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info('$table')", null).use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                columns += cursor.getString(nameIndex)
            }
        }
        return columns
    }

    /**
     * DB_VERSION 41 as shipped in v4.20 (2025-11-02, e862b05).
     *
     * Keep this snapshot unchanged while v4.20 is inside the supported upgrade window. When the
     * compatibility baseline advances, replace this whole fixture from a newer release rather than
     * editing it toward the current schema.
     */
    private fun createV420Schema(db: SQLiteDatabase) {
        listOf("number_filter", "content_filter", "quick_copy").forEach { table ->
            db.execSQL(
                """
                CREATE TABLE $table (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    pattern TEXT,
                    pattern_extra TEXT,
                    pattern_flag INTEGER DEFAULT 0,
                    pattern_extra_flag INTEGER DEFAULT 0,
                    description TEXT,
                    priority INTEGER,
                    blacklist INTEGER,
                    flag_call_sms INTEGER,
                    channel_id TEXT DEFAULT Low,
                    schedule TEXT DEFAULT '',
                    block_type INTEGER DEFAULT 0,
                    block_type_config TEXT DEFAULT ''
                )
                """.trimIndent()
            )
        }

        db.execSQL(
            """
            CREATE TABLE notification_channel (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                channel_id TEXT UNIQUE,
                importance INTEGER,
                mute INTEGER,
                sound TEXT,
                icon TEXT,
                icon_color INTEGER,
                led INTEGER,
                led_color INTEGER,
                grouping TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_channel_id ON notification_channel(channel_id)")

        db.execSQL(
            """
            CREATE TABLE spam (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                peer TEXT UNIQUE,
                reason INTEGER,
                reason_extra TEXT,
                time INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_peer ON spam(peer)")

        db.execSQL(
            """
            CREATE TABLE push_alert (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                enabled INTEGER,
                package_name TEXT,
                body TEXT,
                body_flags INTEGER,
                duration INTEGER
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_pkg_name ON push_alert(package_name)")

        db.execSQL(
            """
            CREATE TABLE api_query (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT,
                actions TEXT,
                enabled INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE api_report (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT,
                actions TEXT,
                enabled INTEGER,
                auto_report_types INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE bot (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                description TEXT,
                schedule TEXT,
                actions TEXT,
                enabled INTEGER,
                work_uuid TEXT UNIQUE,
                last_log TEXT,
                last_log_time INTEGER
            )
            """.trimIndent()
        )

        listOf("call", "sms").forEach { table ->
            db.execSQL(
                """
                CREATE TABLE $table (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    peer TEXT,
                    time INTEGER,
                    result INTEGER,
                    reason LONG,
                    read INTEGER,
                    extra_info TEXT,
                    expanded INTEGER
                )
                """.trimIndent()
            )
        }
    }

    private class DelegatingDbHelper(
        context: Context,
        name: String,
        private val delegate: Db,
    ) : SQLiteOpenHelper(context, name, null, Db.DB_VERSION) {
        override fun onConfigure(db: SQLiteDatabase) = delegate.onConfigure(db)
        override fun onCreate(db: SQLiteDatabase) = delegate.onCreate(db)
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            delegate.onUpgrade(db, oldVersion, newVersion)
        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) =
            delegate.onDowngrade(db, oldVersion, newVersion)
        override fun onOpen(db: SQLiteDatabase) = delegate.onOpen(db)
    }

    companion object {
        private const val V420_DB_VERSION = 41
        private const val FRESH_DB_NAME = "db_schema_fresh_test.db"
        private const val UPGRADE_DB_NAME = "db_schema_upgrade_test.db"
    }
}
