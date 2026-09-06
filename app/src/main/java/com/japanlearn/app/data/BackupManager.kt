package com.japanlearn.app.data

import com.japanlearn.app.data.local.AppDatabase
import com.japanlearn.app.data.local.DailyStudyEntity
import com.japanlearn.app.data.local.ReviewRecordEntity
import com.japanlearn.app.data.local.UserProgressEntity
import com.japanlearn.app.data.local.WrongAnswerEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 备份文件结构：学习进度 + 错题本 + 每日统计 + 复习记录（v0.3 数据备份与恢复）。 */
@Serializable
data class BackupFile(
    val schema: String,
    val version: Int,
    val exportedAt: Long,
    val progress: List<UserProgressEntity> = emptyList(),
    val wrongAnswers: List<WrongAnswerEntity> = emptyList(),
    val dailyStudy: List<DailyStudyEntity> = emptyList(),
    val reviewRecords: List<ReviewRecordEntity> = emptyList(),
)

/** 导入结果统计，用于恢复完成后的反馈文案。 */
@Serializable
data class BackupSummary(
    val progress: Int,
    val wrongAnswers: Int,
    val dailyStudy: Int,
    val reviewRecords: Int,
)

/**
 * 数据备份与恢复。导出为带 schema 标识的 JSON；导入按各表主键做合并写入
 * （备份中的同键行覆盖本地，其余保留），避免误导入清空现有进度。
 */
class BackupManager(private val db: AppDatabase) {

    suspend fun exportJson(): String = BackupFileSchema.encode(export())

    suspend fun export(): BackupFile = BackupFile(
        schema = BackupFileSchema.CURRENT,
        version = BackupFileSchema.VERSION,
        exportedAt = System.currentTimeMillis(),
        progress = db.progressDao().allOnce(),
        wrongAnswers = db.wrongAnswerDao().allOnce(),
        dailyStudy = db.dailyStudyDao().allOnce(),
        reviewRecords = db.reviewRecordDao().allOnce(),
    )

    suspend fun import(file: BackupFile): BackupSummary {
        val normalized = BackupFileSchema.normalizeForImport(file)
        normalized.progress.forEach {
            // progress 的业务键是 (contentType, contentId)：沿用本地行主键做覆盖，避免 unique index 冲突
            val local = db.progressDao().get(it.contentType, it.contentId)
            db.progressDao().upsert(it.copy(rowId = local?.rowId ?: 0))
        }
        normalized.wrongAnswers.forEach { db.wrongAnswerDao().upsert(it) }
        normalized.dailyStudy.forEach { db.dailyStudyDao().upsert(it) }
        normalized.reviewRecords.forEach { db.reviewRecordDao().insert(it) }
        return BackupSummary(
            normalized.progress.size,
            normalized.wrongAnswers.size,
            normalized.dailyStudy.size,
            normalized.reviewRecords.size,
        )
    }
}

/** 备份文件的解析与校验（纯逻辑，便于单元测试）。 */
object BackupFileSchema {
    const val CURRENT = "japanlearn-backup"
    const val VERSION = 1

    private val json = Json { ignoreUnknownKeys = true }

    /** 解析并校验备份文本；格式错误或 schema 不符返回 null。 */
    fun parse(text: String): BackupFile? = try {
        val file = json.decodeFromString<BackupFile>(text)
        file.takeIf { it.schema == CURRENT }
    } catch (_: Exception) {
        null
    }

    /**
     * 导入前的归一化：清掉备份中的自增主键（rowId / id 交由本地重新分配），
     * 使同一份备份可重复导入、且不与本地已有记录的主键冲突。
     */
    fun normalizeForImport(file: BackupFile): BackupFile = file.copy(
        progress = file.progress.map { it.copy(rowId = 0) },
        reviewRecords = file.reviewRecords.map { it.copy(id = 0) },
    )

    fun encode(file: BackupFile): String = json.encodeToString(file)
}
