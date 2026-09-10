package com.japanlearn.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Room 迁移 SQL 抽出为常量，便于 JVM 测试断言，不跑模拟器。 */
object AppMigrations {

    const val SQL_1_2_KANA_GROUP =
        "ALTER TABLE kana ADD COLUMN groupName TEXT NOT NULL DEFAULT 'seion'"

    const val SQL_2_3_WORDS_LEVEL =
        "ALTER TABLE words ADD COLUMN level TEXT NOT NULL DEFAULT 'N5'"

    const val SQL_2_3_GRAMMAR_LEVEL =
        "ALTER TABLE grammar ADD COLUMN level TEXT NOT NULL DEFAULT 'N5'"

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SQL_1_2_KANA_GROUP)
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SQL_2_3_WORDS_LEVEL)
            db.execSQL(SQL_2_3_GRAMMAR_LEVEL)
        }
    }
}
