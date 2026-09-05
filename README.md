# JapanLearn — 日语学习 Android App（MVP）

面向日语零基础 / JLPT N5-N4 初学者的轻量化学习 App，核心是
**「今日学习 → 即时练习 → SRS 间隔复习 → 看到进步」** 的每日学习闭环。

产品需求见 [PRD.md](PRD.md)（§17 为评审决策记录，与正文冲突处以 §17 为准）。

## 功能（MVP 已实现）

| 模块 | 说明 |
|---|---|
| 今日学习 | 首页聚合今日任务（新词 / 语法 / 复习）、进度条、连续学习天数 |
| 五十音 | 平/片假名网格、详情（罗马音 + 示例词）、测验 |
| N5 单词 | 200 个单词（假名 / 释义 / 词性 / 例句 / 分类），新词学习会话 + 选择题练习 |
| N5 语法 | 30 条语法（释义 / 接续 / 说明 / 例句 / 练习题），学习会话 + 详情页练习 |
| SRS 复习 | 简单间隔调度（不认识→会话内重排，模糊 1d / 熟悉 ×1.5 / 熟练 ×2，上限 60 天），每日复习限流 |
| 错题本 | 答错自动收录，复习答对自动移除（含五十音） |
| 学习统计 | 连击天数、累计学习时长 / 新词 / 复习量、近 7 天柱状图 |
| 每日一句 | 30 条场景句（日常 / 餐厅 / 便利店 / 旅游 / 学校 / 工作 / 动漫），词汇拆解 |
| 发音 | 系统 TTS（ja-JP），离线零成本 |
| 学习目标 | 每日新词数（5/10/15/20）、语法数、复习上限可调 |

明确未做（PRD §13/§17.2）：登录与云同步、AI 助手、后端服务。

## 技术栈

Kotlin 2.0 · Jetpack Compose (Material3) · Navigation Compose · Room (KSP) ·
kotlinx-serialization · Coroutines/ViewModel · 手工依赖注入（`AppContainer`）

- minSdk 26 / targetSdk 35 / compileSdk 35，JDK 17
- 完全离线：课程内容以版本化 JSON 打包于 `app/src/main/assets/content/`，学习数据存 Room
- 架构：UI → ViewModel → Repository → Room（内容装载器 `ContentLoader` 按版本入库）

## 设计系统与动效

- **配色（和色）**：藍（主色）× 朱（连续学习/强调）× 抹茶（掌握/成功）× 茜（错误），
  浅色 = 和纸底 `ui/theme/Color.kt`，深色 = 墨夜；掌握度四档语义色经 `LocalJapanColors` 提供
- **字体**：Manrope 可变字体（OFL，`res/font`）负责拉丁/数字，中日文回退系统字体（离线零成本）
- **形状**：统一圆角体系（小 10 / 卡片 20 / 大面板 28），见 `ui/theme/Shape.kt`
- **动效**：`ui/motion/Motion.kt` 统一令牌（强调曲线 + 弹簧物理）——
  级联入场 `StaggerIn`、数字滚动 `AnimatedCounterText`、进度条/环、答题对错反馈（对勾弹出 + 错误摇晃）、
  完成彩带 `ConfettiBurst`、按压物理 `pressScale`、骨架屏 shimmer、页签/二级页差异化导航转场
- **无障碍**：全部动效尊重系统「动画时长缩放 = 0」（减弱动态时退化为静态/即时切换）

```
app/src/main/java/com/japanlearn/app/
├── domain/        # 纯函数业务逻辑：SrsScheduler / QuizGenerator / StreakCalculator / ReviewPlanner
├── data/
│   ├── content/   # assets JSON 的 DTO
│   ├── local/     # Room 实体 / DAO / 数据库
│   └── repositories/
├── ui/            # home / learn / kana / words / grammar / review / stats / profile / sentence
└── util/          # DateProvider（可注入时钟）、JapaneseTts
```

## 构建与测试

```bash
./gradlew assembleDebug          # 构建 APK（app/build/outputs/apk/debug/）
./gradlew testDebugUnitTest      # 运行单元测试
```

要求：JDK 17、Android SDK Platform 35（`local.properties` 中配置 SDK 路径或使用 `ANDROID_HOME`）。

## 测试覆盖（PRD §17.8 强制要求）

- `SrsSchedulerTest`：四级掌握度的间隔/到期时间、间隔递增与 60 天上限、掌握判定
- `QuizGeneratorTest`：选项数量、含正确答案、不重复、双向词卡、种子可复现、小内容池退化
- `StreakCalculatorTest`：跨天连击、中断归零、今天未学仍延续
- `ReviewPlannerTest`：每日复习限流截断与顺延
- `ContentParsingTest`：内容 JSON schema 解析与未知字段向前兼容
- `UiMathTest`：今日进度/柱状图占比/入场级联延迟的边界（0 除、截断、封顶）
- `FormatTest`：学习时长展示格式（h/m、负数钳制）

## 内容扩充

四个 JSON 文件均带 `version` 字段；追加内容后递增版本号，App 启动时会自动重新装载（学习进度不受影响）：

- `kana.json`：五十音（h/k/r + 示例词）
- `words_n5.json`：单词（ja/kana/romaji/zh/pos/cat/example/exampleZh）
- `grammar_n5.json`：语法（title/meaning/connection/explanation/examples/exercises）
- `sentences.json`：每日一句（scene/ja/zh/breakdown）
