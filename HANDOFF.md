# JapanLearn 交接文档

> 写给零上下文的新会话。接手前请完整读完本文，尤其是第 6 节「踩坑清单」。

## 1. 项目是什么

**JapanLearn**——日语学习 Android App（Kotlin + Jetpack Compose + Room，完全离线、无后端、无需账号）。面向 N5/N4 初学者，核心是「今日学习 → 即时练习 → SRS 间隔复习 → 看到进步」的每日闭环。

- 仓库：https://github.com/Ageha6912/JapanLearn（公开，远端 origin 已配置）
- 需求文档：`PRD.md`（§17 为 v0.1 评审决策记录，**§18 为 v0.2 决策记录**，**§19 为 v0.5–v0.7 决策记录与产品缺口清单**，与正文冲突时以 §17/§18/§19 为准）
- 优化方案：`OPTIMIZATION.md`（**Accepted**，用户 2026-09-09 确认 Q1–Q3 推荐项；结论已于 2026-09-12 写入 PRD §19）
- Git 身份（仓库级已配置）：`Ageha <ageha6912@gmail.com>`，勿用其他身份提交
- 已发布：**v0.8.0**（tag + GitHub Release，正式签名 APK）。`versionName = "0.8.0"` / `versionCode` 19

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
./gradlew :app:testDebugUnitTest    # 单元测试（当前 169 项），必须全绿才能交付
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

## 4. 当前任务：v0.8.0 已发布（2026-09-12）

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

下一步：v0.9 收口：PR-C（versionName 0.9.0 / versionCode 20 → assembleRelease → 模拟器回归 → tag + GitHub Release）。PR-A 听力训练、PR-B 每日一句 180 均已交付。

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
- Room 当前 **version 4**（`exportSchema = true`，快照 `app/schemas/com.japanlearn.app.data.local.AppDatabase/4.json`）。v1→v2 = kana.`groupName`；v2→v3 = words/grammar.`level`；v3→v4 = FSRS 字段（`stability/difficulty/lapses/fsrsState`）。SQL 在 `AppMigrations.kt`。
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
