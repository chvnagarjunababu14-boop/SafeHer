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
}
