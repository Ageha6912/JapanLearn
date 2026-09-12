# JapanLearn 交接文档

> 写给零上下文的新会话。接手前请完整读完本文，尤其是第 6 节「踩坑清单」。

## 1. 项目是什么

**JapanLearn**——日语学习 Android App（Kotlin + Jetpack Compose + Room，完全离线、无后端、无需账号）。面向 N5/N4 初学者，核心是「今日学习 → 即时练习 → SRS 间隔复习 → 看到进步」的每日闭环。

- 仓库：https://github.com/Ageha6912/JapanLearn（公开，远端 origin 已配置）
- 需求文档：`PRD.md`（§17 为 v0.1 评审决策记录，**§18 为 v0.2 决策记录**，**§19 为 v0.5–v0.7 决策记录与产品缺口清单**，与正文冲突时以 §17/§18/§19 为准）
- 优化方案：`OPTIMIZATION.md`（**Accepted**，用户 2026-09-09 确认 Q1–Q3 推荐项；结论已于 2026-09-12 写入 PRD §19）
- Git 身份（仓库级已配置）：`Ageha <ageha6912@gmail.com>`，勿用其他身份提交
- 已发布：**v1.2.1**（tag + GitHub Release，正式签名 APK）。`versionName = "1.2.1"` / `versionCode` 24

## 2. 环境速查

| 项 | 值 |
|---|---|
| JDK | 17（Temurin，`JAVA_HOME` 已设） |
| Android SDK | `E:\Android\Sdk`（`ANDROID_HOME` 已设），Platform 35 |
| Gradle | 用项目 wrapper（`./gradlew`）；Gradle 8.9 发行版在 `/e/tools/gradle-8.9` |
| 模拟器 | AVD `medium_phone`（API 35），插件 `android_start_emulator` 启动（工具调用超时 30s 属正常，用 `adb devices` 等就绪） |
| gh CLI | 已登录 `Ageha6912` |
| Python | 3.12（Anaconda），有 PIL；无 cairosvg（缺原生 DLL），SVG 预览用 Chrome headless：`chrome --headless=new --screenshot=...` |

构建/测试：

```bash
./gradlew :app:assembleDebug        # debug APK
./gradlew :app:assembleRelease      # 正式签名 APK（keystore 已配置，见第 6 节坑 9）
./gradlew :app:testDebugUnitTest    # 单元测试（当前 223 项），必须全绿才能交付
python tools/validate_content.py    # 内容校验，必须通过才能改内容；CI 已跑
```

## 3. 已完成

### v0.1.0（MVP，已发布）
- 五十音/单词/语法学习闭环、SRS 复习（模糊 1d/熟悉×1.5/熟练×2 上限 60 天）、每日限流 30、错题本、统计、每日一句、系统 TTS
- 46 项单元测试；PRD §17 决策记录；README 美化（`assets/readme/` 下 hero.svg/workflow.svg/showcase.png + 再生成脚本）

### v0.2.0（已发布，正式签名）
- **内容**：单词 504（11 分类）、五十音 101（清音46/浊音25/拗音30，`group` 字段）、语法 50；校验管线 `tools/validate_content.py` + 扩充/合并脚本（`tools/expand_kana.py`、`merge_words.py`、`merge_grammar.py`、批次文件 `new_words_b*.json`）
- **功能**：听音选词（30% 变体，`AudioQuizPolicy`）、每日复习提醒（WorkManager 20:00/可开关/深链复习页，`work/ReviewReminderWorker.kt` + `util/ReminderScheduler.kt`）、五十音三分组 + 测验分组抽题与题量 10/20/全部、单词列表掌握度色点、四 Tab 标题固定
- **发布工程**：R8 minify + 资源收缩（APK 1.5MB）、正式签名接入（keystore）、GitHub Actions 门禁 CI（`.github/workflows/ci.yml`：55 测试 + assembleDebug）
- 55 项单元测试全绿；PRD §18 决策记录；README 数字已同步

## 4. 当前任务：v1.2.1 已发布（2026-09-12，INTERNET 权限热修复）

### v0.8 规划（PRD §19.6，grilling 两轮定案）
- 主题：学习目标系统 + 个性化每日学习计划——级别 + 可选目标日期，倒推夹 5/10/15/20 档位、按周校准；存 SharedPreferences 不动 Room；纯函数 `StudyPlanner` 带测试；`ReviewPlanner`/FSRS 零接触
- UI：我的 → 「学习目标」页 + 首页任务卡只读摘要行
- Onboarding 搭车：首启三步可跳过（含目标收集），独立 PR；零进度首页「先学五十音」横幅
- 切分：PR-A 目标+计划 → PR-B Onboarding → PR-C 收口 v0.8.0（versionCode 19）；PR-B 赶不上随 v0.8.1

### PR-A 已交付（2026-09-12）— 学习目标 + 个性化每日计划
- `domain/StudyPlanner.kt` 纯函数：按目标日期倒推推荐档位（剩余量÷剩余天数向上取整，夹 5/10/15/20 封顶 20，超限记 overdue）、`progressPercent`、周校准判定 `shouldRecalibrate`；16 项单测
- `SettingsRepository`：新增 `goal_level` / `goal_target_epoch_day` / `goal_tier_applied_epoch_day` 三个 SharedPreferences key；`setGoal` 重置校准戳、`applyGoalTier` 应用档位并盖戳；**未动 Room schema**
- `AppContainer.recalibrateGoal()`：应用启动每周校准（距上次自动应用 ≥7 天才重算），目标设置/更新后立即校准一次
- UI：我的 → 「学习目标」入口卡 + 新页 `ui/profile/GoalScreen.kt`（级别选择 / 达成日 1·3·6 个月预设 / 推荐档采用 / 清除目标）；原「学习目标」卡改名「每日任务量」；首页任务卡只读摘要行（点击跳目标页，未设目标不显示）
- 全量 152 测全绿；versionName 未动（版本号只在 PR-C 收口改）

### PR-B 已交付（2026-09-12）— Onboarding 首启三步引导
- `domain/OnboardingGate.kt` 纯函数 + 4 项单测：只对「未看过引导且零进度（learnedWords==0 && learnedGrammar==0）」的用户显示，老用户与跳过/完成者永不打扰
- `SettingsRepository.onboardingDone`（SharedPreferences key `onboarding_done`），完成或跳过即置位
- `ui/onboarding/OnboardingScreen.kt` 全屏覆盖层（HomeScreen 根 Box 顶层）：① 闭环说明（学习/练习/复习三行）→ ② 当前水平（复用 `settings.setStudyLevel`）+ 可选目标（复用 `setGoal` + `recalibrateGoal`，选完立即生效）→ ③ 直达五十音（nav KANA）或直接开始；任意一步可跳过，BackHandler=跳过
- 防闪烁：`progressLoaded`（首个进度 Flow 到达）后才判定门槛，避免升级老用户在计数到达前闪现引导；覆盖层空白区 `blockClicks()` 防点击透传
- 零进度「先学五十音」横幅沿用既有 `HomeKanaIntro`，未重复实现
- 全量 156 测全绿；versionName 未动

### PR-C 已发布（2026-09-12）— v0.8.0 收口
- versionName 0.8.0 / versionCode 19；重新 assembleRelease（坑 5）
- 模拟器 release 回归通过（坑 11 版本号已核对）：全新安装三步引导（水平/目标/日期选择、达成日 +90 天计算正确）、首页目标摘要行及其跳转、学习目标页（N5 进度 0/504 词 + 0/50 语法、建议档位 10 = 当前档位、清除入口）、我的页入口卡与「每日任务量」改名卡、学习会话冒烟
- tag v0.8.0 + GitHub Release（JapanLearn-v0.8.0.apk）；回归截图在 `.screenshots/v080_*.png`（未入库）

### v0.9 规划（PRD §19.7，grilling 两轮定案）
- 主题：完整听力训练（系统 TTS；预生成音频维持无排期）——三题型：听音辨词（四选一）/ 听写假名（复用打字题判分，仅已学词、不足 5 个回退听音辨词）/ 听句选义（三选一）
- 入口：学习 Tab 新增「听力训练」入口卡 → 会话页（题量 5/10/20）；不新增 Tab；配比 40/30/30；不限流、不进首页任务卡
- 联动边界：逐题落统计 + 答错进错题本，**不推进 SRS 调度**
- 交互：进题自动播一次 + 重播按钮；TTS 不可用走 v0.7.x 引导链；结算复用现有会话结算页
- 切分：PR-A 听力训练（`ListeningQuizGenerator` 纯函数带测试）→ PR-B 每日一句 120→180 → PR-C 收口 v0.9.0（versionCode 20）；PR-B 素材延迟不阻塞 PR-A

### v0.9 PR-A 已交付（2026-09-12）— 听力训练
- `domain/ListeningQuizGenerator.kt` 纯函数 + 13 项单测：`ListeningMixPolicy` 配比 40/30/30（听写已学 <5 回退听音辨词、句库空回退）、`buildSession`（听音辨词已学优先排前、听写仅已学词、听句同场景干扰优先）、`dictationQuiz`（不泄露词形，接受假名/romaji）、`sentenceQuiz` 三选一
- `QuizKind` 新增 `AUDIO_SENTENCE_TO_ZH`（无外部穷尽 when，安全）
- `ProgressRepository.recordAuxAnswer`：听力对错只同步错题本（答对移除/答错 +1），**不推进 SRS**（§19.7 边界）
- `ui/listening/ListeningSessionScreen.kt`：LOADING→PICK（题量 5/10/20）→QUIZ→DONE；`QuizView` 复用（audioText 非空自带进题自动播一次 + 重播按钮）；答完出 FeedbackText + 下一题；结算含答对数 + 再来一轮 + 彩带；会话开始即 `decideAction` 检查 TTS，不可用直接弹 `VoiceGuideDialog`（已从 private 改 internal）
- 统计：会话结束 `addStudy(秒数)` 落时长；不进首页任务卡、不限流
- 入口：学习 Tab 第 4 张 LearnEntry「听力训练」（VolumeUp 图标）+ 路由 `Routes.LISTENING`
- 全量 169 测全绿；versionName 未动

### v0.9 PR-B 已交付（2026-09-12）— 每日一句 120 → 180
- 新场景 **购物 / 交通 / 就医**：`validate_content.py` 的 `SCENES` 白名单按坑 16 同步扩展（三项均 §19.7 点名）
- 批次 `tools/new_sentences_b3.json`（65 条写稿，5 条与现有库撞句被按 ja 去重跳过，净增 60）；覆盖购物试穿/退换/支付、交通问路/换乘/迟到致歉、就医症状/预约/探病慰问等
- `sentences.json` **180 条**，version 3 → 5（两次合并各 +1）；`validate_content.py` 全部通过
- `ContentScaleTest` / `ContentExpansionTest` 断言同步 180 / version 5

### PR-C 已发布（2026-09-12）— v0.9.0 收口
- versionName 0.9.0 / versionCode 20；重新 assembleRelease（坑 5）
- 模拟器回归通过（坑 11 版本号已核对）：全新安装引导正常（v0.8 特性无回归）、学习 Tab 听力训练入口卡、题量选择、听句选义与听音辨词两题型渲染 + 答题判定 + 反馈流转、**TTS 引擎缺失时进会话直接弹引导对话框（§19.7 前置检测实测生效，AVD 无 TTS 恰好验证）**
- tag v0.9.0 + GitHub Release（JapanLearn-v0.9.0.apk）；回归截图 `.screenshots/v090_*.png`（未入库）
- CI 门禁：收口 commit 全绿（169 测 + assembleDebug）

### v1.0 规划（PRD §19.8，grilling 两轮定案）
- 主题：N5/N4 课程化——11 分类即 11 单元（友好命名），语法按难度均分；软推进（浏览自由、当前单元=第一个未完成单元、今日新词从当前单元顺序取）；单元测试 10 题 soft 检查点（复用 QuizVariantPicker）
- 数据：words/grammar JSON 加 `unit` 字段 + Room v4→v5 加 unit 列（沿 level 先例），version 升位自动重装
- UI：学习 Tab「单词/语法」两卡合并为「课程」大卡 → 单元列表 → 单元详情；全量词表保留为「全部单词」入口
- 搭车：学习成果页（我的 Tab，零新表聚合：时长/词数/连击/单元完成 x/22/正确率/五十音）
- 切分：PR-A 数据与迁移 → PR-B 课程 UI → PR-C 成果页（可并行）→ PR-D 收口 v1.0.0（versionCode 21 + README 里程碑重写 + showcase 重生成）
- 分配结论：AI 助手（语法解释/句子纠错）v1.0 不做；建议 v1.1 以 BYOK（用户自备 API Key 直连）形态做，不建后端不加登录

### v1.0 PR-A 已交付（2026-09-12）— unit 归属 + Room v5
- `tools/assign_units.py`：词按分类映射 unit 1..11（人物=1…身体=11，全级别统一）；语法按级别内难度顺序均分（N5 各单元 4–5 条、N4 各 3–4 条）；words version 6→7、grammar 5→6
- `validate_content.py`：unit 与分类一致性校验（词）+ unit 范围 1..11 校验（语法），全部通过
- Room **v4→v5**：words/grammar 加 `unit INTEGER NOT NULL DEFAULT 0`（沿 level 列先例），schema 快照 5.json 入库；内容 version 升位触发重装，orphan 进度按 id 关联不受影响
- DTO/实体/装载器透传 unit；`domain/CourseCatalog.kt` 单元目录（11 个友好标题，人物与称呼…身体与健康）
- 测试 +7：迁移 SQL/schema v5、单元覆盖（22 级别×单元组合）、标题互异；全量 176 测全绿

### v1.0 PR-B 已交付（2026-09-12）— 课程 UI
- `domain/CoursePointer.kt`（+8 测）：当前单元指针纯函数（第一个未完成单元，全完则停最后；覆盖格式 "N5:3"，空/级别不匹配/越界回退自动）
- `domain/CheckpointBuilder.kt`（+3 测）：单元检查点出题（复用 QuizVariantPicker 混合题型，池小循环取词）
- DAO/仓库：`UnitProgressRow`（LEFT JOIN user_progress 的 unit 维度计数，**主键列是 rowId 不是 id**——踩过一次 KSP SQLITE_ERROR）、`newWordsByUnit`、`byLevelAndUnit`；ContentRepository 包装层
- `SettingsRepository`：`course_unit_override` key + 检查点最佳成绩存取（`checkpoint_best_{level}_{unit}`，只留最佳）
- **今日取词改造**：`WordSessionViewModel` 新词从「覆盖 ?: 自动当前单元」顺序取；单元学完回退全池，级别学完直接 DONE
- **学习 Tab**：「单词」「语法」两卡合并为「课程」大卡（第 N 单元 · 标题 · 已学 x/y + 进度条）；五十音/听力卡不动
- `ui/course/CourseScreens.kt`：单元列表页（级别切换、当前单元高亮、检查点最佳、全部单词/语法入口）→ 单元详情页（词表带掌握度色点、语法列表、设为当前单元）→ 单元测试页（10 题，对错只落错题本 + 统计，成绩记最佳，彩带结算）
- 路由：`course` / `courseUnit/{level}/{unit}` / `courseCheckpoint/{level}/{unit}`
- 全量 187 测全绿；versionName 未动

### v1.0 PR-C 已交付（2026-09-12）— 学习成果页
- `ui/stats/AchievementsScreen.kt` + 我的 Tab 学习者卡片双按钮（查看学习统计 / 学习成果）；路由 `achievements`
- 指标（零新表纯聚合）：累计时长（`formatStudyDuration`）、已学/掌握单词、已学语法、当前连击、**历史最长连击**（`StreakCalculator.longestStreak` 纯函数 +3 测）、单元完成 x/22（`completedUnitCount` 聚合两级别）、复习正确率（`review_records` 全量 correct 计数）
- **取舍（§19.8「零新表」）**：听力正确率不做（v0.9 听力只记错题不记对错，无数据源）；五十音进度不做（五十音无进度表）；待后续版本需要时再引入记录
- 全量 190 测全绿；versionName 未动

### PR-D 已发布（2026-09-12）— v1.0.0 收口
- versionName 1.0.0 / versionCode 21；重新 assembleRelease（坑 5）
- 模拟器回归通过（坑 11 版本号已核对）：全新安装引导、首页、课程卡、单元列表/详情、检查点出题、成果页各指标卡与空态
- **README 里程碑更新**：徽章 190 测、介绍段加课程化、SRS 表格换 FSRS 描述、功能总览加课程/听力/成果/目标行、每日一句 180、测试清单更新、目录结构更新；`build_showcase.py` PICKS 换为课程化六图（首页/课程/单元详情/检查点/听力/成果）并重新生成 showcase.png
- tag v1.0.0 + GitHub Release（JapanLearn-v1.0.0.apk）；回归截图 `.screenshots/v100_*.png`（未入库）

### v1.1 规划（PRD §19.9，grilling 两轮定案）
- 主题：AI 语法助手 BYOK——用户自备 Key 直连 OpenAI 兼容端点（Base URL + Key + 模型名三字段，预设 DeepSeek/GLM/OpenAI），不建后端不加登录
- **§17.1 修订**：核心闭环保持完全离线；AI 是唯一可选在线增强——无 Key 则入口全隐藏，学习数据永不出设备
- 功能：助手页三模式（语法解释/句子纠错/翻译）+ 语法详情/错题本两个上下文预填入口；一次性返回非流式
- Key：SharedPreferences 明文本地存，不进备份不打日志；每日限额默认 20 次/天（10/20/50/不限），按日期本地计数
- 切分：PR-A 网络与配置基座（OkHttp + AiClient 接口 + 设置区 + 限额纯函数）→ PR-B 助手页 + 上下文入口 → PR-C 收口 v1.1.0（versionCode 22）；Prompt/解析/限额全走纯函数单测，网络用 fake

### v1.1 PR-A 已交付（2026-09-12）— 网络与配置基座
- 新依赖：`com.squareup.okhttp3:okhttp:4.12.0`（自带 R8 规则）
- `domain/AiAssistant.kt`（+9 测）：`AiMode` 三模式、`AiPrompts`（三套中文系统提示词 + 上下文附加）、`AiConfig`（三字段 isConfigured、normalizeBaseUrl 自动补 `/chat/completions`、DeepSeek/GLM/OpenAI 三预设、每日限额档 10/20/50/-1）、`AiQuota`（canCall/remaining，-1 = 不限）
- `domain/AiWire.kt`（+7 测）：请求体构建（model + system/user 两条消息）、响应 content 解析（容错多余字段）、错误信息提取（服务端 message 优先，401/404/429/5xx 兜底文案）
- `data/ai/AiClient.kt`：`AiClient` 接口（单测用 fake）+ `OpenAiCompatibleClient`（IO 线程、15s/60s 超时、`AiException` 直接带可读中文文案、CancellationException 透传）
- `SettingsRepository`：`ai_base_url/ai_api_key/ai_model/ai_daily_limit` 四个 key + `ai_calls_{date}` 每日计数；AppContainer 暴露 `aiClient`
- 设置页（我的）新增「AI 助手（可选，需联网）」卡：未配置 → 三预设 chips + 三字段表单（Key 密码样式）+ 保存并启用；已配置 → 模型/端点展示 + 限额 chips + 清除配置；两态均带隐私说明
- 全量 206 测全绿；versionName 未动

### v1.1 PR-B 已交付（2026-09-12）— AI 助手页与上下文入口
- `ui/ai/AiAssistantScreen.kt`：三模式 chips（语法解释/句子纠错/翻译）+ 多行输入 + 发送（loading 态「思考中…」）→ 结果卡（按模式换标题）；错误卡（`AiException` 文案直接展示）；顶部「需联网 · 费用由 Key 承担 · 今日剩余 N 次」行
- 未配置态：页面可达但只显示引导卡 + 「去设置」（跳我的 Tab）；额度用完发送时给明确提示
- 上下文预填：导航参数 `mode/input/context`（`Uri.encode` 传输，Navigation 自动解码）；带上下文时显示「已带入教材上下文」提示条
- **入口三处（全部 gated by `aiConfigured` 组合流）**：我的 AI 卡「开始对话」、语法详情「问 AI 讲解」（带入 title/meaning/connection）、错题本每条「AI 讲解」（带入 primary）
- 每次成功调用 `incrementAiCalls(今天)` 落计数；`aiConfigured` 是 SettingsRepository 上的 combine 流，复用注意
- 本 PR 为纯 UI 层，无新增纯逻辑（发送闸门/额度判定复用已测的 AiQuota/AiConfig）；全量 206 测全绿；versionName 未动

### PR-C 已发布（2026-09-12）— v1.1.0 收口
- versionName 1.1.0 / versionCode 22；重新 assembleRelease（坑 5）
- 模拟器回归通过（坑 11 版本号已核对，假 Key 走真实请求）：未配置态（三预设+表单+保存灰）→ 预设填入/Key 遮蔽/模型自动填 → 已配置态（启用徽章/开始对话/限额/隐私/清除）→ 助手页发送 → 「网络不可用或接口无法连接」错误卡实测（模拟器无法连通外网 LLM API 属预期；401 等分支由 AiWireTest 覆盖）
- **回归抓到并修复一个真 bug**：AI 助手页发送按钮与输入框重叠（`StaggerIn` 是 Box 布局，两个组件塞进同一 StaggerIn 会叠绘——已在 fix 提交外包 Column；**新增多组件块时不要塞进同一个 StaggerIn**）
- README 增补 AI 助手行 + 测试数 206；tag v1.1.0 + GitHub Release（JapanLearn-v1.1.0.apk）；截图 `.screenshots/v110_*.png`（未入库）

### v1.2 PR-C 已发布（2026-09-12）— v1.2.0 收口
- versionName 1.2.0 / versionCode 23；重新 assembleRelease（坑 5）
- 模拟器回归通过（坑 11 版本号已核对，完整闭环实测）：学习会话答错 + 自评不认识 → 错题入本（复习 Tab 错题本 1 条待攻克 + 突击卡出现）→ 突击会话重出该错题（1/1 全出）→ 答对 → 结算「错题本已全部清空！」——答对移除语义端到端验证
- README 同步（突击行 + 词库 1104 + 测试 213）；tag v1.2.0 + GitHub Release（JapanLearn-v1.2.0.apk）；截图 `.screenshots/v120_*.png`（未入库）
- **坐标坑提醒**：模拟器截图是缩略图（900×2000），设备实际 1080×2400——点击坐标必须 ×1.2 换算，或用 `android_ui_describe` 拿真实 bounds

### v1.2.1 热修复（2026-09-12）— 用户真机报「网络不可用或接口无法连接」
- **根因**：App 纯离线起家，manifest 从未声明 `INTERNET`；v1.1 加 AI 助手时遗漏 → 所有请求在 socket 层被系统拦截，走进兜底文案
- **为什么回归没发现**：模拟器假 Key 测试同样报「网络不可用」，被误判为「模拟器没网」——两种原因（无权限/无网络）共享同一兜底文案，无法区分。**正确姿势：假 Key 若请求真正发出应收到 401 文案「API Key 无效或无权限」，收到网络错误即说明请求根本没出去**
- 修复：manifest 补 `INTERNET` + `ManifestTest` 守护测试（215 测全绿）；验证方式 `adb shell dumpsys package com.japanlearn.app | grep INTERNET`（granted=true）
- 用户真机覆盖 v1.2.1 后即可正常使用 AI 助手（配置保留）

### v1.3 规划（PRD §19.11，grilling 两轮定案 + 真机反馈调整）
- **真机反馈**：AI 回答质量还行（Prompt 不动）、等结果太久 → 主题定为 **AI 流式输出**（原推荐「统计增强」让位，挪 v1.4）
- 流式：同端点 `stream: true`，SSE 逐行解析（纯函数带单测）；首字到达即打字机；中断保留部分文本；无停止按钮（离开即取消）；**首个增量到达即计数**
- 搭车：N4 语法二批（87 → ~120，N4 每单元 3-4 → 6-7 条）
- 切分：PR-A 流式管线 → PR-B 语法二批 → PR-C 收口 v1.3.0（versionCode 25）；统计增强/汉字专项排 v1.4 候选

### v1.3 PR-A 已交付（2026-09-12）— AI 流式管线
- `AiWire.requestBody` 加 `stream` 参数（false 时不出现该字段）；新增 `parseStreamDelta(line)` 纯函数（+8 测）：`data: {...}` 增量 / `[DONE]` / `: keep-alive` 注释 / 角色块无 content / 空 choices / 残缺 JSON 全覆盖
- `AiClient` 接口新增 `stream(request, onDelta): String`；`OpenAiCompatibleClient` 用 `body.source().readUtf8Line()` 逐行读 SSE（OkHttp read timeout 是字节间超时，60s 对流式安全）；非 2xx 读全量 body 走既有错误文案
- VM：`streaming` 状态（流式全程禁用发送防并发）；首个增量到达即 `incrementAiCalls`（等价 200 已收）；流中断保留已收文本 + 错误卡并列显示；离开页面 ViewModel 取消流
- UI：按钮三态「思考中… / 回答中… / 发送」；结果卡文本逐步增长（打字机）
- 全量 223 测全绿；versionName 未动

### v1.2 规划（PRD §19.10，grilling 两轮定案）
- 主题：错题与 SRS 深度联动——**错题突击会话**（复习 Tab 入口，三类错题各有出题通路：word 混合题型 / kana romaji 四选一 / grammar 自带练习；10 题循环取；recordAuxAnswer 判分：答对移除、答错 +1；不推 SRS dueAt）+ **复习队列错题优先**（dueWords/dueGrammar LEFT JOIN wrong_answers 排序，纯排序可回滚）
- 统计口径不混：突击时长落 addStudy，对错不进复习正确率
- 搭车：N4 词二批 +300（总库 804 → 1104，批次管线 + unit 归属）
- 推迟：流式输出（等 AI 真实使用反馈）、统计增强（等数据基建需求）
- 切分：PR-A 突击会话 + 队列加权 → PR-B N4 词二批 → PR-C 收口 v1.2.0（versionCode 23）

### v1.2 PR-A 已交付（2026-09-12）— 错题突击 + 队列加权
- `domain/DrillBuilder.kt`（+7 测）：错题池编排纯函数——word 走 QuizVariantPicker 混合题型、kana 走 kanaQuiz、grammar 用自带练习（VM 解析 exercisesJson 映射为 `DrillGrammarExercise`）；洗牌循环取、已删内容跳过
- `ui/review/WrongAnswerDrillScreen.kt`：突击会话（LOADING→QUIZ→DONE），判分走 `recordAuxAnswer`（答对移除、答错 +1，不推 SRS）；结算「答对 x/y · 剩余 N 道待清」，全部清空撒彩带；「再来一轮」重载错题池
- **队列加权**：`dueWords`/`dueGrammar` 加 `LEFT JOIN wrong_answers`，`ORDER BY CASE WHEN wa.contentId IS NOT NULL THEN 0 ELSE 1 END, p.dueAt`（错题优先，纯排序可回滚）
- 入口：复习 Tab 错题本卡下方「错题突击」卡（`wrongCount > 0` 才显示，Bolt 图标）
- 新坑：**新图标必须补显式 import**（`Icons.Filled.Bolt` 不 import 就是 Unresolved，与图标是否存在无关）；文件内 private 状态类被 public VM 属性暴露时直接改 public（与 WordSessionUiState 惯例一致）
- 全量 213 测全绿；versionName 未动

### v1.2 PR-B 已交付（2026-09-12）— N4 词二批 +300
- 词库 **804 → 1104**（N4 300 → 600），words version 8 → 11（分批合并各 +1）
- 批次文件 `tools/new_words_n4_b5..b9.json`（322 条写稿，与库内既有词去重后净增 300）；覆盖人物家族称呼/时间时段/食材调味/地点设施/日用品/动作动词/形容词/副词等，每词带例句
- **merge_words.py 已补 unit 自动归属**（按分类映射，非法分类直接报错）——后续批次无需再跑 assign_units
- **TypeAnswerNormalizer 金标 allowlist +6**（w821/w941/w1057/w1066/w1067/w1069）：片假名长音符「ー」无法由罗马音表产生，属 allowlist 设计场景；助言 romaji jyogen→jogen 实修
- 全量 213 测全绿；validate 通过；versionName 未动
- 分配结论：预生成音频正式移出 v0.8（无排期可选 PR，门禁声库书面授权）；埋点推迟至内测；内容扩充为常驻并行线；听力训练留 v0.9

### v0.7.5（已发布）— PR-R51 停写旧 content_version
- `ContentLoader` 不再写 `meta.content_version` 加总 key；装载事务末尾 `MetaDao.delete(LEGACY_TOTAL)` 清理 0.5.x 双写残留
- **仍读取**旧 key：`hasLegacyTotalOnly` 判定 0.4.x 升级并强制四文件全量重装，升级路径不受影响
- `MetaDao` 新增 `delete(key)`。内容无变更时也会删旧 key

### v0.7.4（已发布）— 日语 TTS 判定收紧 + 音色选择
真机结论（Android 15 国行）：中文默认 TTS **会读日语**，但只念汉字跳过假名（「休みの間に予習しておきます。」→ 只念「休間習」），且 `setLanguage(ja)`/voice 列表都可能谎报可用。

- **非 Google 引擎一律不信任日语**（`verifiedJapaneseStatus(..., usingGoogleTts=false)` → `LANG_NOT_SUPPORTED`），点击发音弹「缺少语音引擎」引导装 Google TTS
- 仅本地已安装 ja voice 可用；网络 voice 视为缺数据
- 我的 → 发音：列出已下载日语音色（`listInstalledJapaneseVoices`），RadioButton 切换 + 试听；`settings.ttsVoiceName` 持久化，`JapaneseTts.setPreferredVoice` 下次发音生效
- 显示名：`ja-JP-Standard-A` →「标准 A」（`voiceDisplayName`）

### v0.7.1（已发布）— 点击发音 TTS 修复
- 每次 `speak` 重套日语；优先已安装的 `ja-JP` 本地 voice
- voice 列表为空时不误判为不支持；日语 voice 全未下载走「下载语音数据」而不是再装引擎
- 走媒体音轨（`USAGE_MEDIA` / `STREAM_MUSIC`）+ 短暂音频焦点，避开中文 ROM 静音的无障碍音轨
- manifest 补 `TTS_SERVICE` / `INSTALL_TTS_DATA` / `TTS_SETTINGS` 的 `<queries>`
- 真机诊断仍先抓 `logcat -s JapaneseTts`

v0.7.0：Room v4 FSRS 字段；默认 `FsrsScheduler`（ts-fsrs v5.4.2 long-term，`enable_short_term=false`；Again 仍 `dueAt=now`）；自评文案不变；已掌握 `stability >= 21`。回滚：`AppContainer` 改回 `SrsScheduler`，勿删 `MIGRATION_3_4`。

下一步：v1.3 继续：PR-B N4 语法二批（87 → ~120，批次管线 + 单元归属）→ PR-C 收口 v1.3.0。PR-A 已交付。

v0.4.3（中文引擎修复）：用户真机「只读汉字跳过假名 + 不弹引导」——默认引擎是中文引擎，init 成功且 availableLanguages 谎报日语。重构 JapaneseTts：装有 Google TTS（com.google.android.tts）时显式按包名初始化不走默认；可用性用 setLanguage(JAPAN) 返回值实测（MISSING_DATA→数据引导 / NOT_SUPPORTED→引擎引导）；manifest 加 <queries>（Android 11+ 包可见性，漏加会查不到 Google TTS）；点击时 refreshJapaneseStatus 保证下载后立即生效。

v0.3.1（小版本）：日语语音包缺失引导——TtsButton 点击时检测（`JapaneseTts.needsVoiceData()` 实时查 availableLanguages），缺失弹对话框 → `INSTALL_TTS_DATA` → 兜底 TTS 设置页 → 都没有则 Toast。模拟器上 INSTALL_TTS_DATA 解析不到（Android 15 AVD），兜底路径已实测；多数真机 Google TTS 支持该 action。74 项测试全绿。

v0.4.2（横条消失修复）：用户真机报首页今日一句横条不见——根因是 HomeViewModel 用 sentencesAll().first() 一次性读句子，全新安装时 seed 晚于首页打开则读到空表、句子永久置空。改为收集 Room Flow（空列表不覆盖）。覆盖安装用户不受影响，**任何一次性读取 assets-seeded 表的地方都要警惕这个竞态**。

v0.4.1（发音引导修复）：用户真机报「点发音无声且无引导」——根因是无 TTS 引擎的设备 TextToSpeech 初始化失败/永不回调，旧引导只覆盖「引擎正常但缺日语数据」。现在 JapaneseTts 三态（WAITING/READY/FAILED）+ 1.5s 超时兜底 + retryInit（慢启动可恢复）；点击决策纯函数 decideAction（SPEAK/GUIDE_VOICE_DATA/GUIDE_ENGINE）；无引擎引导走应用商店 Google TTS → Play 网页 → TTS 设置 → Toast；诊断日志 logcat -s JapaneseTts。**诊断真机发音问题先抓这个 tag**。

此前 v0.4.0 交付记录：首页右上角快捷设置卡片（外观三选 ThemeMode 持久化 / 提醒开关 / 全部设置入口）+ 主题明暗切换接入 MainActivity；今日一句改底部横条 + 弹窗（场景/句子/发音/词汇拆解）；通用容器变换弹窗 `ui/motion/TransformCard.kt`（PopupAnchor.TopEnd/BottomCenter，spring 0.72/260 轻微过冲，底部锚点 bottom padding 96dp 让卡片坐在横条上方）；背景不缩放只加遮罩。修复：Tab 路由裸 navigate 压栈导致底部导航失效（共享 `NavHostController.navigateToTab()`，底部导航/深链/设置卡片三处统一）。76 项测试全绿。

此前 v0.3.x 交付记录：

v0.3.0 已按方案全部交付并发布（tag v0.3.0 + GitHub Release，正式签名 APK，CI 全绿）：

- **内容**：N4 首批 300 词 + 30 条语法（words.json 804 / grammar.json 80，schema 加 `level` 字段）；每日一句 30 → 60；批次词库在 `tools/new_words_n4_b*.json`、`tools/new_grammar_n4.json`
- **功能**：学习页 N5/N4 级别切换（`settings.studyLevel` 持久化，新词队列按级别取，复习不分级）；汉字题型（`QuizGenerator.kanjiQuiz` + `KanjiQuizPolicy` 25% 概率，看假名选汉字干扰项限定汉字形 + 排除同音）；数据备份与恢复（`data/BackupManager.kt` + SAF，导入按主键归一化合并，`BackupFileSchema.normalizeForImport`）；桌面小组件（Glance，`widget/TodayWidget.kt`，点击直达复习 Tab，30 分钟轮询）
- **工程**：Room v2→v3（words/grammar 加 level 列）；CI 加 assembleRelease；版本 0.3.0 / versionCode 4
- 回归发现并修复：备份导入自增主键冲突（commit 342bf2f）；汉字题纯假名干扰项（afcafa2 内已含修复）

后续路线以 `OPTIMIZATION.md` 为准（已确认：orphan 进度保留、v0.5–v0.6 继续系统 TTS、FSRS 不改自评文案）。登录同步 / AI / 真人发音仍非默认路径。

## 5. 关键架构事实（改代码前必读）

- 手工依赖注入：`AppContainer`（`JapanLearnApp.container`），Compose 侧经 `LocalAppContainer` 获取；无 Hilt
- 内容流：`assets/content/*.json`（每文件 `version`）→ `ContentSeedPlanner` 决定重装哪些表 → `ContentLoader.seedIfNeeded()` 一次 `withTransaction` 写入 Room（进度表不级联删）。**0.7.5 起停写并删除**旧加总 key `content_version`（仅读：0.4.x 升级判定）。首页/统计计数用 COUNT Flow，已学/到期 JOIN 内容表。
- Room 当前 **version 5**（`exportSchema = true`，快照 `app/schemas/com.japanlearn.app.data.local.AppDatabase/5.json`）。v1→v2 = kana.`groupName`；v2→v3 = words/grammar.`level`；v3→v4 = FSRS 字段（`stability/difficulty/lapses/fsrsState`）；v4→v5 = words/grammar.`unit`（课程单元，PRD §19.8）。SQL 在 `AppMigrations.kt`。
- SRS 调度 `domain/SrsScheduler.kt`（纯函数）；练习生成 `domain/QuizGenerator.kt`（纯函数，含听音变体 `AudioQuizPolicy`）；连击 `StreakCalculator`；限流 `ReviewPlanner`——这些都有单元测试，**改动必须同步补测试**（全局规则：每次改动必须有测试且全绿才能交付）
- 统计逐题实时落库（每评一题 `stats.addStudy`），不要改回"会话结束才落库"
- 设计系统：和色（藍 #1B3A5C × 桜 #C75B5B × 和纸 #F7F5F0）+ Manrope 字体 + `ui/motion/Motion.kt` 动效令牌（`StaggerIn`/`AnimatedCounterText` 等，尊重系统减弱动画）
- 学习/复习/首页/我的四个 Tab 标题都是「固定头部 + 独立滚动区」结构，新增页面沿用此模式

## 6. 踩坑清单（绝对不要再踩）

**编译类**
1. Kotlin 块注释支持嵌套：注释里写 `content/*.json` 会开启嵌套注释导致 "Unclosed comment" 编译失败。注释中不要出现 `/*` 字样
2. Compose 组件签名：**函数类型参数必须放参数列表最后**（调用方用尾随 lambda），`Modifier` 用命名参数传递。曾有 `AppButton(text, onClick, enabled, modifier)` 导致全项目连锁报错
3. `Text(word.pos, fontSize=…, Modifier.padding(...))`——Modifier 作位置参数传错位置也报错，一律 `modifier =`
4. `Surface(onClick=...)`、`TopAppBar`、`FilterChip` 等需要 `@OptIn(ExperimentalMaterial3Api::class)`

**构建/发布类**
5. **改了 versionName/versionCode 后必须重新 assembleRelease**，否则发出去的 APK 还是旧版本号（v0.2 就差点把 0.1.1 发出去）
6. CI 在 GitHub Actions 上跑：`gradlew` 的可执行位必须保留（已用 `git update-index --chmod=+x gradlew` 修复；Windows 上重写该文件可能再次丢失权限）
7. `gh release create` 的 `文件#显示名` 语法**不生效**，资产名要用「先复制改名 → delete-asset → upload」的方式重命名
8. debug 与 release 签名不同：互升版本会 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，先 `adb uninstall com.japanlearn.app` 再装。v0.1.0（debug 签名）→ v0.2.0+（release 签名）之间就是这种情况，Release Notes 已写明
9. 正式签名：`japanlearn-release.jks` + `keystore.properties` 在项目根目录，**已被 gitignore 且从未进过 git 历史（已验证）**；`build.gradle.kts` 条件加载（文件存在才签名）。**用户需自行备份，丢了永久无法更新签名应用**——提醒过用户
10. R8 已开启（`isMinifyEnabled = true` + 资源收缩），kotlinx-serialization 的 keep 规则在 `app/proguard-rules.pro`，新增反射依赖时记得补规则

**模拟器/环境类**
11. **模拟器异常退出会回滚到旧 quick-boot 快照**——已安装的 APK 可能变回旧版本。每次冷启动后先 `dumpsys package com.japanlearn.app | grep versionName` 核对版本再回归
12. **模拟器可能被其他会话/应用占用**（曾出现健身 App 在前台），点击前必须先截图确认前台是 JapanLearn，绝不能盲点坐标
13. 访问 github.com:443 间歇性 Connection reset——**重试即可**，不要改配置
14. 模拟器频繁掉线：`android_start_emulator` 调用超时是正常的，`sleep` 后用 `adb devices` 确认

**内容类**
15. `tools/merge_words.py` 的批次文件查找路径是 `Path(__file__).parent.glob(...)`（曾写错层级导致 0 条合并，靠输出统计发现）
16. `validate_content.py` 的字符集规则：假名允许 2 字符（拗音）和 `〜`(U+301C 量词前缀)；例句允许汉字+标点。新增内容类型时同步扩展校验
17. 内容已含同音异形词（はし=筷子/橋）与近重复，去重键是 `(ja, zh)` 与 `kana+zh`，合并脚本会跳过并打印

**v0.3 新增**
27. TextToSpeech 在部分 ROM（无默认引擎/冷启动引擎未就绪）上 onInit 会失败或永不回调——发音引导必须覆盖 FAILED 态，且不能依赖初始化回调一定发生（超时兜底 + retryInit）
28. **国行中文 TTS 会「能读日语」但只念汉字**（真机 v0.7.2 实测「休みの間に…」→「休間習」）。`setLanguage(ja)` 可能返回可用、voice 列表也可能列出假 ja voice。**非 Google TTS 一律判日语不可用**，点发音引导装 Google TTS；不要试图信任中文引擎的日语能力
29. 发音诊断真机仍先抓 `adb logcat -s JapaneseTts`；用户无 adb PATH 时用 `E:\Android\Sdk\platform-tools\adb.exe`
20. 备份导入必须先过 `BackupFileSchema.normalizeForImport`（清自增主键）+ progress 按 (contentType, contentId) 查本地 rowId——直接 upsert 会 UNIQUE constraint 崩溃
21. Glance 1.1.0：`clickable` 在 `androidx.glance.action` 包（不是 `androidx.glance`）；`actionStartActivity(intent)` 在 `androidx.glance.appwidget.action`；Intent 的 extras 会保留（深链 extra 直接 putExtra 即可）
22. Git Bash 下 adb shell 里的 `/sdcard/...` 会被路径转换，用 `MSYS_NO_PATHCONV=1 adb shell "..."` 一行式命令
24. Tab 路由（HOME/LEARN/REVIEW/PROFILE）之间的跳转必须走 `navigateToTab()`（popUpTo+saveState/restoreState+singleTop），裸 navigate 会把 Tab 压成返回栈层级导致底部导航失效
25. Compose `Modifier.padding` 没有 horizontal+top+bottom 的工厂重载，混用要用 start/top/end/bottom 四参数形式
26. PaddingValues 弹窗锚点：TransformCardPopup 的 BottomCenter 锚点 bottom=96dp 是给「今日一句横条」留位的，新增底部锚点入口时核对横条/导航高度
23. 合并脚本 `merge_words.py` 的 glob 已改为 `new_words_*.json`（旧批次文件还在，重跑只会全部跳过，无副作用）；`merge_grammar.py` 批次文件名走第一个命令行参数

**仓库纪律**
18. `.screenshots/14_release_home.png`、`15_v011_about.png`、`16_v011_final_scroll.png` 三张截图**用户明确不入库**，保持未跟踪状态
19. 用户在意的验证顺序：改内容 → `validate_content.py` 通过；改代码 → `testDebugUnitTest` 全绿；发版 → 模拟器 release 包回归走查（核对本节坑 11 的版本号）

## 7. 其他备忘

- 用户工作模式：多用「继续」推进；用 grilling/grill-me skill 做版本规划（两轮问答 + 推荐项，用户常回答"都按你推荐"）
- README 顶部展示墙由 `assets/readme/build_showcase.py` 生成（会裁掉截图底部系统导航栏）
- 本地预览 SVG 用 Chrome headless（见第 2 节），渲染产物（hero.png 等）不要提交
- 模拟器上的旧学习数据可能因快照回滚/卸载丢失——本地数据无备份机制前（v0.3 交付前），不要对数据丢失做任何承诺
