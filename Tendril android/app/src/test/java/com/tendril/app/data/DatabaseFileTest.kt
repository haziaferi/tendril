package com.tendril.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * §9.10 — the set-aside promise, checked against the real file.
 *
 * [DatabaseRecoveryTest] pins the *sequence* of [openOrRecover] with a fake. This file asks the
 * question that fake cannot: when the file under `tendril.db` is not a database at all, does the
 * production Android open path ([openTendrilDatabase]) ever get to *see* that? It runs on
 * Robolectric because the answer lives inside `android.database.sqlite`: the framework's
 * `SQLiteDatabase.open()` has its own corruption handler, and if that handler deletes the file and
 * reopens an empty one before the probe runs, the "set aside, never deleted" rule is true of the
 * helper and false of the app.
 */
@RunWith(RobolectricTestRunner::class)
// `application = Application::class`: with `isIncludeAndroidResources` (P11, 2026-09-18) Robolectric reads the real
// manifest and would start `TendrilApp`, whose container opens the database before this test corrupts the file.
@Config(sdk = [36], application = android.app.Application::class)
class DatabaseFileTest {

    @Test
    fun `a file that is not a database is set aside with its bytes intact, and the app opens empty`() {
        val context = RuntimeEnvironment.getApplication()
        val file = context.getDatabasePath(TENDRIL_DB_NAME)
        file.parentFile!!.mkdirs()
        val garbage = "this is not a database\n".toByteArray()
        file.writeBytes(garbage)

        val open = openTendrilDatabase(context)
        try {
            // (a) recovery was reported — the probe saw the broken file, not a fresh empty one
            assertTrue("openTendrilDatabase never saw the corrupt file", open.recovered)

            // (b) the original bytes survive under the set-aside name, and the file was not deleted
            val setAside = file.parentFile!!.listFiles { f -> f.name.startsWith(file.name + UNOPENABLE_SUFFIX) }!!
            assertEquals("exactly one set-aside copy", 1, setAside.size)
            assertArrayEquals(garbage, setAside.single().readBytes())

            // (c) the app is running on an empty database in the file's old place
            assertTrue(file.exists())
            assertTrue(runBlocking { open.database.pageDao().getAll() }.isEmpty())
        } finally {
            open.database.close()
        }
    }
}
