package com.safeher.app

import androidx.sqlite.db.SupportSQLiteDatabase
import com.safeher.app.data.local.db.JourneyEventEntity
import com.safeher.app.data.local.db.SafeHerDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class DatabaseMigrationTest {

    @Test
    fun migration_2_3_versionsAreCorrect() {
        assertEquals(2, SafeHerDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, SafeHerDatabase.MIGRATION_2_3.endVersion)
    }

    @Test
    fun migration_1_2_versionsAreCorrect() {
        assertEquals(1, SafeHerDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, SafeHerDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun roomGeneratedDatabase_definesEventTypeAsNotNull() {
        val resourceStream = SafeHerDatabase::class.java.classLoader
            ?.getResourceAsStream("com/safeher/app/data/local/db/SafeHerDatabase_Impl\$1.class")
        org.junit.Assert.assertNotNull("SafeHerDatabase_Impl\$1.class must exist in classpath", resourceStream)
        val content = resourceStream!!.readBytes().toString(Charsets.ISO_8859_1)
        assertTrue(
            "Room's generated schema in SafeHerDatabase_Impl must specify eventType TEXT NOT NULL",
            content.contains("`eventType` TEXT NOT NULL")
        )
    }

    @Test
    fun migration_2_3_executesExactSqlMatchingEntitySchema() {
        val executedStatements = mutableListOf<String>()

        val proxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedStatements.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        SafeHerDatabase.MIGRATION_2_3.migrate(proxy)

        assertTrue(
            "MIGRATION_2_3 must execute at least 3 statements (table + 2 indexes)",
            executedStatements.size >= 3
        )

        val createTableSql = executedStatements.first { it.contains("CREATE TABLE", ignoreCase = true) }
        assertTrue("Must create journey_events table", createTableSql.contains("journey_events"))
        assertTrue("eventType must be TEXT NOT NULL", createTableSql.contains("`eventType` TEXT NOT NULL"))
        assertTrue("journeyId must be INTEGER NOT NULL", createTableSql.contains("`journeyId` INTEGER NOT NULL"))
        assertTrue("timestamp must be INTEGER NOT NULL", createTableSql.contains("`timestamp` INTEGER NOT NULL"))
        assertTrue("Must have foreign key with CASCADE delete", createTableSql.contains("ON DELETE CASCADE"))

        val indexStatements = executedStatements.filter { it.contains("CREATE INDEX", ignoreCase = true) }
        assertEquals(2, indexStatements.size)
        assertTrue(indexStatements.any { it.contains("index_journey_events_journeyId") })
        assertTrue(indexStatements.any { it.contains("index_journey_events_timestamp") })
    }

    @Test
    fun migration_3_4_versionsAreCorrect() {
        assertEquals(3, SafeHerDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, SafeHerDatabase.MIGRATION_3_4.endVersion)
    }

    @Test
    fun migration_3_4_executesExactSqlMatchingEntitySchema() {
        val executedStatements = mutableListOf<String>()

        val proxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedStatements.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        SafeHerDatabase.MIGRATION_3_4.migrate(proxy)

        assertTrue(
            "MIGRATION_3_4 must execute alter and index statements",
            executedStatements.isNotEmpty()
        )

        assertTrue(executedStatements.any { it.contains("ALTER TABLE `guardians` ADD COLUMN `userId` TEXT DEFAULT NULL") })
        assertTrue(executedStatements.any { it.contains("DROP INDEX IF EXISTS `index_guardians_phoneNumber`") })
        assertTrue(executedStatements.any { it.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_guardians_userId_phoneNumber`") })
        assertTrue(executedStatements.any { it.contains("CREATE INDEX IF NOT EXISTS `index_guardians_userId`") })

        assertTrue(executedStatements.any { it.contains("ALTER TABLE `journeys` ADD COLUMN `userId` TEXT DEFAULT NULL") })
        assertTrue(executedStatements.any { it.contains("CREATE INDEX IF NOT EXISTS `index_journeys_userId`") })
        assertTrue(executedStatements.any { it.contains("CREATE INDEX IF NOT EXISTS `index_journeys_userId_status`") })
    }

    @Test
    fun migration_4_5_versionsAreCorrect() {
        assertEquals(4, SafeHerDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, SafeHerDatabase.MIGRATION_4_5.endVersion)
    }

    @Test
    fun migration_4_5_executesExactSqlMatchingEntitySchema() {
        val executedStatements = mutableListOf<String>()

        val proxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedStatements.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        SafeHerDatabase.MIGRATION_4_5.migrate(proxy)

        assertTrue(
            "MIGRATION_4_5 must execute alter table and sos_events creation statements",
            executedStatements.isNotEmpty()
        )

        assertTrue(
            "Must add isSosEnabled to guardians",
            executedStatements.any { it.contains("ALTER TABLE `guardians` ADD COLUMN `isSosEnabled` INTEGER NOT NULL DEFAULT 1") }
        )
        val createSosEventsSql = executedStatements.first { it.contains("CREATE TABLE IF NOT EXISTS `sos_events`") }
        assertTrue("Must create sos_events table", createSosEventsSql.contains("sos_events"))
        assertTrue("Must define id as INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL", createSosEventsSql.contains("`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL"))
        assertTrue("Must define timestamp as INTEGER NOT NULL", createSosEventsSql.contains("`timestamp` INTEGER NOT NULL"))
        assertTrue("Must define recipientsCount as INTEGER NOT NULL", createSosEventsSql.contains("`recipientsCount` INTEGER NOT NULL"))
        assertTrue("Must define successCount as INTEGER NOT NULL", createSosEventsSql.contains("`successCount` INTEGER NOT NULL"))
        assertTrue("Must define failureCount as INTEGER NOT NULL", createSosEventsSql.contains("`failureCount` INTEGER NOT NULL"))
        assertTrue(
            "Must create userId index on sos_events",
            executedStatements.any { it.contains("index_sos_events_userId") }
        )
        assertTrue(
            "Must create timestamp index on sos_events",
            executedStatements.any { it.contains("index_sos_events_timestamp") }
        )
    }

    @Test
    fun migration_5_6_versionsAreCorrect() {
        assertEquals(5, SafeHerDatabase.MIGRATION_5_6.startVersion)
        assertEquals(6, SafeHerDatabase.MIGRATION_5_6.endVersion)
    }

    @Test
    fun migration_5_6_executesExactSqlMatchingEntitySchema() {
        val executedStatements = mutableListOf<String>()

        val proxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedStatements.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        SafeHerDatabase.MIGRATION_5_6.migrate(proxy)

        assertTrue(
            "MIGRATION_5_6 must execute alter table and index statements",
            executedStatements.isNotEmpty()
        )

        assertTrue(
            "Must add isMonitoringActive to journeys",
            executedStatements.any { it.contains("ALTER TABLE `journeys` ADD COLUMN `isMonitoringActive` INTEGER NOT NULL DEFAULT 0") }
        )
        assertTrue(
            "Must add lastLatitude to journeys",
            executedStatements.any { it.contains("ALTER TABLE `journeys` ADD COLUMN `lastLatitude` REAL DEFAULT NULL") }
        )
        assertTrue(
            "Must add lastLongitude to journeys",
            executedStatements.any { it.contains("ALTER TABLE `journeys` ADD COLUMN `lastLongitude` REAL DEFAULT NULL") }
        )
        assertTrue(
            "Must add lastLocationTimestamp to journeys",
            executedStatements.any { it.contains("ALTER TABLE `journeys` ADD COLUMN `lastLocationTimestamp` INTEGER NOT NULL DEFAULT 0") }
        )
        assertTrue(
            "Must add overdueSafetyCheckTimestamp to journeys",
            executedStatements.any { it.contains("ALTER TABLE `journeys` ADD COLUMN `overdueSafetyCheckTimestamp` INTEGER NOT NULL DEFAULT 0") }
        )
        assertTrue(
            "Must add safetyCheckAcknowledged to journeys",
            executedStatements.any { it.contains("ALTER TABLE `journeys` ADD COLUMN `safetyCheckAcknowledged` INTEGER NOT NULL DEFAULT 0") }
        )
        assertTrue(
            "Must add overdueEscalationDeadline to journeys",
            executedStatements.any { it.contains("ALTER TABLE `journeys` ADD COLUMN `overdueEscalationDeadline` INTEGER NOT NULL DEFAULT 0") }
        )
        assertTrue(
            "Must add overdueEscalationAttempted to journeys",
            executedStatements.any { it.contains("ALTER TABLE `journeys` ADD COLUMN `overdueEscalationAttempted` INTEGER NOT NULL DEFAULT 0") }
        )
        assertTrue(
            "Must add journeyId to sos_events",
            executedStatements.any { it.contains("ALTER TABLE `sos_events` ADD COLUMN `journeyId` INTEGER DEFAULT NULL") }
        )
        assertTrue(
            "Must create journeyId index on sos_events",
            executedStatements.any { it.contains("index_sos_events_journeyId") }
        )
    }

    @Test
    fun migration_6_7_versionsAreCorrect() {
        assertEquals(6, SafeHerDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, SafeHerDatabase.MIGRATION_6_7.endVersion)
    }

    @Test
    fun migration_6_7_addsBatteryAlertEnabledDefaultOff() {
        val executedStatements = mutableListOf<String>()

        val proxy = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedStatements.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        SafeHerDatabase.MIGRATION_6_7.migrate(proxy)

        assertTrue(
            "Must add isBatteryAlertEnabled defaulting to OFF (0)",
            executedStatements.any {
                it.contains("ALTER TABLE `guardians` ADD COLUMN `isBatteryAlertEnabled` INTEGER NOT NULL DEFAULT 0")
            }
        )
    }
}
