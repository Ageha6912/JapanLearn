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

    const val SQL_3_4_STABILITY =
        "ALTER TABLE user_progress ADD COLUMN stability REAL NOT NULL DEFAULT 0"
    const val SQL_3_4_DIFFICULTY =
        "ALTER TABLE user_progress ADD COLUMN difficulty REAL NOT NULL DEFAULT 0"
    const val SQL_3_4_LAPSES =
        "ALTER TABLE user_progress ADD COLUMN lapses INTEGER NOT NULL DEFAULT 0"
    const val SQL_3_4_FSRS_STATE =
        "ALTER TABLE user_progress ADD COLUMN fsrsState TEXT NOT NULL DEFAULT 'New'"
    const val SQL_3_4_SEED =
        "UPDATE user_progress SET stability = CAST(intervalDays AS REAL), difficulty = 5.0, fsrsState = 'Review' WHERE intervalDays > 0"

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SQL_3_4_STABILITY)
            db.execSQL(SQL_3_4_DIFFICULTY)
            db.execSQL(SQL_3_4_LAPSES)
            db.execSQL(SQL_3_4_FSRS_STATE)
            db.execSQL(SQL_3_4_SEED)
        }
    }

    // v5（PRD §19.8 课程化）：words/grammar 加 unit 列；内容 version 升位后由装载器重写真实值
    const val SQL_4_5_WORDS_UNIT =
        "ALTER TABLE words ADD COLUMN unit INTEGER NOT NULL DEFAULT 0"
    const val SQL_4_5_GRAMMAR_UNIT =
        "ALTER TABLE grammar ADD COLUMN unit INTEGER NOT NULL DEFAULT 0"

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SQL_4_5_WORDS_UNIT)
            db.execSQL(SQL_4_5_GRAMMAR_UNIT)
        }
    }

    // v6（PRD §19.14 汉字专项）：新增 kanji 表；进度仍挂 user_progress 的 contentType=kanji
    const val SQL_5_6_KANJI_TABLE =
        "CREATE TABLE IF NOT EXISTS `kanji` (`id` TEXT NOT NULL, `char` TEXT NOT NULL, `zh` TEXT NOT NULL, " +
            "`onJson` TEXT NOT NULL, `kunJson` TEXT NOT NULL, `examplesJson` TEXT NOT NULL, " +
            "`level` TEXT NOT NULL, `order` INTEGER NOT NULL, PRIMARY KEY(`id`))"

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(SQL_5_6_KANJI_TABLE)
        }
    }
}
