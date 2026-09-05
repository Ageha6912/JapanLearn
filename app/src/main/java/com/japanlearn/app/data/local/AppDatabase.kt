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
    version = 1,
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
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "japanlearn.db").build()
    }
}
