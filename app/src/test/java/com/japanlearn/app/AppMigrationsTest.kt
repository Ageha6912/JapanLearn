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
    fun `Room schema v3 快照已入库`() {
        val candidates = listOf(
            File("schemas/com.japanlearn.app.data.local.AppDatabase/3.json"),
            File("app/schemas/com.japanlearn.app.data.local.AppDatabase/3.json"),
        )
        assertTrue(
            "expected AppDatabase/3.json under app/schemas; cwd=${File(".").canonicalPath}",
            candidates.any { it.isFile },
        )
    }
}
