package com.japanlearn.app

import com.japanlearn.app.data.BackupFile
import com.japanlearn.app.data.BackupFileSchema
import com.japanlearn.app.data.local.DailyStudyEntity
import com.japanlearn.app.data.local.UserProgressEntity
import com.japanlearn.app.data.local.WrongAnswerEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 备份文件解析与 schema 校验（v0.3 数据备份与恢复）。 */
class BackupFileSchemaTest {

    private val progress = UserProgressEntity(
        rowId = 0,
        contentType = "word",
        contentId = "w001",
        mastery = 2,
        intervalDays = 3,
        reviewCount = 1,
        dueAt = 1_700_000_000_000,
        status = "learning",
        learnedAt = 1_699_000_000_000,
        lastReviewedAt = 1_700_000_000_000,
    )

    @Test
    fun `备份文件编码后可完整还原`() {
        val file = BackupFile(
            schema = BackupFileSchema.CURRENT,
            version = BackupFileSchema.VERSION,
            exportedAt = 1_700_000_000_000,
            progress = listOf(progress),
            wrongAnswers = listOf(WrongAnswerEntity("word", "w001", 2, 1_700_000_000_000)),
            dailyStudy = listOf(DailyStudyEntity("2026-09-06", 300, 10, 2, 12)),
            reviewRecords = emptyList(),
        )
        val parsed = BackupFileSchema.parse(BackupFileSchema.encode(file))
        assertNotNull(parsed)
        parsed!!
        assertEquals(file.progress, parsed.progress)
        assertEquals(file.wrongAnswers, parsed.wrongAnswers)
        assertEquals(file.dailyStudy, parsed.dailyStudy)
        assertEquals(BackupFileSchema.CURRENT, parsed.schema)
    }

    @Test
    fun `容忍未知字段 保证备份向前兼容`() {
        val text = """
            {"schema":"japanlearn-backup","version":1,"exportedAt":1700000000000,
             "progress":[],"futureField":123}
        """.trimIndent()
        val parsed = BackupFileSchema.parse(text)
        assertNotNull(parsed)
        assertEquals(0, parsed!!.progress.size)
    }

    @Test
    fun `schema 不符的文件拒绝导入`() {
        val text = """
            {"schema":"other-app-backup","version":1,"exportedAt":1700000000000,"progress":[]}
        """.trimIndent()
        assertNull(BackupFileSchema.parse(text))
    }

    @Test
    fun `损坏的 JSON 拒绝导入`() {
        assertNull(BackupFileSchema.parse("{ not a json "))
        assertNull(BackupFileSchema.parse(""))
    }

    @Test
    fun `导入归一化 清空自增主键避免与本地冲突`() {
        val withIds = BackupFile(
            schema = BackupFileSchema.CURRENT,
            version = 1,
            exportedAt = 0,
            progress = listOf(progress.copy(rowId = 7)),
            reviewRecords = listOf(
                com.japanlearn.app.data.local.ReviewRecordEntity(
                    id = 3, contentType = "word", contentId = "w001",
                    correct = true, masteryAfter = 2, reviewedAt = 1L, nextDueAt = 2L,
                ),
            ),
        )
        val normalized = BackupFileSchema.normalizeForImport(withIds)
        assertEquals(0, normalized.progress.single().rowId)
        assertEquals(0, normalized.reviewRecords.single().id)
        // 归一化不改动其余字段
        assertEquals(progress.copy(rowId = 0), normalized.progress.single())
    }
}
