package com.japanlearn.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WordEntity::class,
        GrammarEntity::class,
        KanaEntity::class,
        SentenceEntity::class,
        UserProgressEntity::class,
        ReviewRecordEntity::class,
        DailyStudyEntity::class,
        WrongAnswerEntity::class,
        MetaEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wordDao(): WordDao
    abstract fun grammarDao(): GrammarDao
    abstract fun kanaDao(): KanaDao
    abstract fun sentenceDao(): SentenceDao
    abstract fun progressDao(): ProgressDao
    abstract fun reviewRecordDao(): ReviewRecordDao
    abstract fun dailyStudyDao(): DailyStudyDao
    abstract fun wrongAnswerDao(): WrongAnswerDao
    abstract fun metaDao(): MetaDao

    companion object {
        /** kana 表新增 group_name 列（v0.2 假名分组）。 */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE kana ADD COLUMN groupName TEXT NOT NULL DEFAULT 'seion'")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "japanlearn.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
