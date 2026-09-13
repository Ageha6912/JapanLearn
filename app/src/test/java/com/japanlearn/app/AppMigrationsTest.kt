package com.japanlearn.app

import com.japanlearn.app.data.local.AppMigrations
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AppMigrationsTest {

    @Test
    fun `1到2 给 kana 加 groupName 列`() {
        assertTrue(AppMigrations.SQL_1_2_KANA_GROUP.contains("ALTER TABLE kana ADD COLUMN groupName"))
    }

    @Test
    fun `2到3 给 words 和 grammar 加 level 列`() {
        assertTrue(AppMigrations.SQL_2_3_WORDS_LEVEL.contains("ALTER TABLE words ADD COLUMN level"))
        assertTrue(AppMigrations.SQL_2_3_GRAMMAR_LEVEL.contains("ALTER TABLE grammar ADD COLUMN level"))
    }

    @Test
    fun `3到4 给进度表加 FSRS 列`() {
        assertTrue(AppMigrations.SQL_3_4_STABILITY.contains("ALTER TABLE user_progress ADD COLUMN stability"))
        assertTrue(AppMigrations.SQL_3_4_FSRS_STATE.contains("fsrsState"))
        assertTrue(AppMigrations.SQL_3_4_SEED.contains("intervalDays > 0"))
    }

    @Test
    fun `4到5 给 words 和 grammar 加 unit 列`() {
        assertTrue(AppMigrations.SQL_4_5_WORDS_UNIT.contains("ALTER TABLE words ADD COLUMN unit"))
        assertTrue(AppMigrations.SQL_4_5_GRAMMAR_UNIT.contains("ALTER TABLE grammar ADD COLUMN unit"))
        assertTrue(AppMigrations.SQL_4_5_WORDS_UNIT.contains("NOT NULL DEFAULT 0"))
    }

    @Test
    fun `5到6 新建 kanji 表`() {
        assertTrue(AppMigrations.SQL_5_6_KANJI_TABLE.contains("CREATE TABLE IF NOT EXISTS `kanji`"))
        assertTrue(AppMigrations.SQL_5_6_KANJI_TABLE.contains("`char` TEXT NOT NULL"))
        assertTrue(AppMigrations.SQL_5_6_KANJI_TABLE.contains("`onJson` TEXT NOT NULL"))
        assertTrue(AppMigrations.SQL_5_6_KANJI_TABLE.contains("`kunJson` TEXT NOT NULL"))
        assertTrue(AppMigrations.SQL_5_6_KANJI_TABLE.contains("`examplesJson` TEXT NOT NULL"))
        assertTrue(AppMigrations.SQL_5_6_KANJI_TABLE.contains("PRIMARY KEY(`id`)"))
    }

    @Test
    fun `Room schema v3 快照已入库`() {
        assertTrue(schemaExists("3.json"))
    }

    @Test
    fun `Room schema v4 快照已入库`() {
        assertTrue(schemaExists("4.json"))
    }

    @Test
    fun `Room schema v5 快照已入库`() {
        assertTrue(schemaExists("5.json"))
    }

    @Test
    fun `Room schema v6 快照已入库`() {
        assertTrue(schemaExists("6.json"))
    }

    private fun schemaExists(name: String): Boolean {
        val candidates = listOf(
            File("schemas/com.japanlearn.app.data.local.AppDatabase", name),
            File("app/schemas/com.japanlearn.app.data.local.AppDatabase", name),
        )
        return candidates.any { it.isFile }
    }
}
