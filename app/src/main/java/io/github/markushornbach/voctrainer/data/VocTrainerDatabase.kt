package io.github.markushornbach.voctrainer.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Book::class, Vocabulary::class, IrregularVerb::class, PracticeDay::class, PointsDay::class, Figure::class, Badge::class
    ],
    // Schema 13 is the base; a later change adds AutoMigration(from = 13, to = 14) here.
    version = 13,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class VocTrainerDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun irregularVerbDao(): IrregularVerbDao
    abstract fun practiceDayDao(): PracticeDayDao
    abstract fun pointsDayDao(): PointsDayDao
    abstract fun figureDao(): FigureDao
    abstract fun badgeDao(): BadgeDao
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var INSTANCE: VocTrainerDatabase? = null

        fun getInstance(context: Context): VocTrainerDatabase =
            INSTANCE ?: synchronized(this) {
                val appContext = context.applicationContext
                INSTANCE ?: builder(appContext, "voctrainer.db").build().also { INSTANCE = it }
            }

        /**
         * The app's database configuration for the file [name]; the migrations come from the
         * annotation. Also used by tests, so they open a database exactly like the app does.
         */
        internal fun builder(context: Context, name: String): RoomDatabase.Builder<VocTrainerDatabase> =
            Room.databaseBuilder(context, VocTrainerDatabase::class.java, name)

        /** UI tests only: an in-memory database instead of the app's own, so a test never touches its data. */
        @VisibleForTesting
        internal fun replaceInstanceForTest(database: VocTrainerDatabase?) {
            INSTANCE = database
        }
    }
}
