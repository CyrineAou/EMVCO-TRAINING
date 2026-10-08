package com.example.worldlineintegrationsdk


import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.worldlineintegrationsdk.db.AppDatabase
import com.example.worldlineintegrationsdk.repository.ReferenceRepository


import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** End-to-end test: fake SDK table → dispatcher → Room → typed read. */
@RunWith(AndroidJUnit4::class)
class ReferenceTablesTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ReferenceRepository

    /** Fresh in-memory database before each test. */
    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
        repo = ReferenceRepository(db.referenceDao())
    }

    /** Closes the database after each test. */
    @After
    fun tearDown() = db.close()

    /** Cards are extracted, blanks/duplicates dropped, lookup works. */
    @Test
    fun blacklist_isExtracted_andUsable() = runBlocking {
        assertEquals(2, repo.onTableReceived(FakeTables.blacklist))
        assertTrue(repo.isBlacklisted("4970100000000001"))
        assertTrue(repo.isBlacklisted("4970100000000002"))
        assertFalse(repo.isBlacklisted("9999999999999999"))
    }

    /** A new blacklist replaces the old one entirely. */
    @Test
    fun blacklist_update_replacesOldList() = runBlocking {
        repo.onTableReceived(SdkTable("BLACKLIST", "v1", listOf(mapOf("cardNumber" to "AAA"))))
        repo.onTableReceived(SdkTable("BLACKLIST", "v2", listOf(mapOf("cardNumber" to "BBB"))))
        assertFalse(repo.isBlacklisted("AAA"))
        assertTrue(repo.isBlacklisted("BBB"))
    }

    /** Floor limit is returned as a typed object. */
    @Test
    fun floorLimit_isTyped() = runBlocking {
        repo.onTableReceived(FakeTables.floorLimit)
        val fl = repo.floorLimitFor("A0000000031010")
        assertEquals(5000L, fl?.amount)
        assertEquals("TND", fl?.currency)
        assertNull(repo.floorLimitFor("UNKNOWN"))
    }

    /** TAC codes are typed, and the AID is normalized to uppercase. */
    @Test
    fun tac_isTyped_andKeyNormalized() = runBlocking {
        repo.onTableReceived(FakeTables.tac)
        val tac = repo.tacFor("a0000000031010")   // any case works
        assertEquals("0010000000", tac?.denial)
        assertEquals("DC4004F800", tac?.online)
        assertEquals("DC4000A800", tac?.default)
    }

    /** Same key in two tables does not collide. */
    @Test
    fun tables_areIsolated() = runBlocking {
        repo.onTableReceived(SdkTable("BLACKLIST", null, listOf(mapOf("cardNumber" to "X1"))))
        assertTrue(repo.isBlacklisted("X1"))
        assertNull(repo.floorLimitFor("X1"))
    }

    /** Unknown tables are ignored without crashing. */
    @Test
    fun unknownTable_isIgnored() = runBlocking {
        assertNull(repo.onTableReceived(SdkTable("NEW_TABLE", null, emptyList())))
    }
}