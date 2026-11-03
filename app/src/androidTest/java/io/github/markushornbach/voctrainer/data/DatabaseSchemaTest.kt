package io.github.markushornbach.voctrainer.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A new database, created with the app's own configuration ([VocTrainerDatabase.builder]), has
 * schema version 13 and matches the exported schema `13.json`, the base of every later migration.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSchemaTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), VocTrainerDatabase::class.java)

    private companion object {
        const val NAME = "schema-test.db"
        const val BASE_VERSION = 13
    }

    @Before
    @After
    fun deleteFile() {
        context.deleteDatabase(NAME)
    }

    @Test
    fun aNewDatabaseMatchesTheExportedBaseSchema() {
        val db = VocTrainerDatabase.builder(context, NAME).build()
        assertEquals(BASE_VERSION, db.openHelper.writableDatabase.version)
        db.close()
        // Opens the file again and validates every table, column, key and index against 13.json.
        helper.runMigrationsAndValidate(NAME, BASE_VERSION, true).close()
    }
}
