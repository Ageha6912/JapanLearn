package com.japanlearn.app

import com.japanlearn.app.util.JapaneseTts
import com.japanlearn.app.util.JapaneseTts.Action
import com.japanlearn.app.util.JapaneseTts.State
import com.japanlearn.app.util.JapaneseTts.VoiceProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 日语 TTS 可用性判定与发音引导决策（v0.4.3 实测 setLanguage 结果）。 */
class JapaneseTtsTest {

    private val langAvailable = android.speech.tts.TextToSpeech.LANG_COUNTRY_AVAILABLE
    private val langMissingData = android.speech.tts.TextToSpeech.LANG_MISSING_DATA
    private val langNotSupported = android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED

    @Test
    fun `日语可用性判定 setLanguage 结果语义`() {
        // 与 JapaneseTts.japaneseUsable / japaneseMissingData 同一规则
        fun usable(status: Int) = status >= android.speech.tts.TextToSpeech.LANG_AVAILABLE
        fun missingData(status: Int) = status == langMissingData
        assertTrue(usable(langAvailable))
        assertFalse(usable(langMissingData))
        assertFalse(usable(langNotSupported))
        assertTrue(missingData(langMissingData))
        assertFalse(missingData(langNotSupported))
    }

    @Test
    fun `非 Google 引擎一律不信任日语 避免中文引擎只读汉字`() {
        // 真机：中文默认引擎 setLanguage(ja) 可能可用、甚至列出假 ja voice，却只念汉字
        assertEquals(
            langNotSupported,
            JapaneseTts.verifiedJapaneseStatus(langAvailable, VoiceProbe.UNKNOWN, usingGoogleTts = false),
        )
        assertEquals(
            langNotSupported,
            JapaneseTts.verifiedJapaneseStatus(langAvailable, VoiceProbe.INSTALLED, usingGoogleTts = false),
        )
        assertEquals(
            langNotSupported,
            JapaneseTts.verifiedJapaneseStatus(langAvailable, VoiceProbe.NONE, usingGoogleTts = false),
        )
        assertEquals(
            langNotSupported,
            JapaneseTts.verifiedJapaneseStatus(langAvailable, VoiceProbe.MISSING_DATA, usingGoogleTts = false),
        )
    }

    @Test
    fun `Google TTS 空 voice 列表时信任 setLanguage`() {
        // Google TTS 偶发 voices() 为空但实际能发音
        assertEquals(
            langAvailable,
            JapaneseTts.verifiedJapaneseStatus(langAvailable, VoiceProbe.UNKNOWN, usingGoogleTts = true),
        )
        assertEquals(
            langNotSupported,
            JapaneseTts.verifiedJapaneseStatus(langNotSupported, VoiceProbe.UNKNOWN, usingGoogleTts = true),
        )
    }

    @Test
    fun `引擎谎报 setLanguage 可用但没有日语 voice 时必须视为不可用`() {
        assertEquals(
            langNotSupported,
            JapaneseTts.verifiedJapaneseStatus(langAvailable, VoiceProbe.NONE, usingGoogleTts = true),
        )
        assertEquals(
            langAvailable,
            JapaneseTts.verifiedJapaneseStatus(langAvailable, VoiceProbe.INSTALLED, usingGoogleTts = true),
        )
    }

    @Test
    fun `日语 voice 全是未安装或仅网络时引导下载数据而不是安装引擎`() {
        assertEquals(
            langMissingData,
            JapaneseTts.verifiedJapaneseStatus(langAvailable, VoiceProbe.MISSING_DATA, usingGoogleTts = true),
        )
        assertEquals(
            Action.GUIDE_VOICE_DATA,
            JapaneseTts.decideAction(State.READY, japaneseUsable = false, japaneseMissingData = true),
        )
    }

    @Test
    fun `已安装日语 voice 时即使 setLanguage 失败也视为可用`() {
        assertEquals(
            android.speech.tts.TextToSpeech.LANG_AVAILABLE,
            JapaneseTts.verifiedJapaneseStatus(
                langNotSupported,
                VoiceProbe.INSTALLED,
                usingGoogleTts = true,
            ),
        )
    }

    @Test
    fun `日语 locale 识别 ja 与 jpn`() {
        assertTrue(JapaneseTts.isJapaneseLocale("ja"))
        assertTrue(JapaneseTts.isJapaneseLocale("JA", "jpn"))
        assertTrue(JapaneseTts.isJapaneseLocale("", "jpn"))
        assertFalse(JapaneseTts.isJapaneseLocale("zh"))
        assertFalse(JapaneseTts.isJapaneseLocale("en", "eng"))
        assertTrue(JapaneseTts.isJapaneseCountry("JP"))
        assertTrue(JapaneseTts.isJapaneseCountry("jpn"))
        assertFalse(JapaneseTts.isJapaneseCountry("CN"))
    }

    @Test
    fun `选 voice 优先已安装的 ja-JP 本地语音`() {
        val chineseFake = JapaneseTts.VoiceCandidate(language = "zh", country = "CN", quality = 500, name = "zh-cn")
        val netJa = JapaneseTts.VoiceCandidate(
            language = "ja", country = "JP", quality = 400, networkRequired = true, name = "ja-jp-net",
        )
        val localJa = JapaneseTts.VoiceCandidate(
            language = "ja", country = "JP", quality = 300, name = "ja-JP-local",
        )
        val notInstalled = JapaneseTts.VoiceCandidate(
            language = "ja", country = "JP", quality = 500, notInstalled = true, name = "ja-missing",
        )
        val picked = JapaneseTts.pickBestJapaneseVoice(listOf(chineseFake, netJa, notInstalled, localJa))
        assertEquals("ja-JP-local", picked?.name)
    }

    @Test
    fun `用户偏好的 voice 优先于自动最高质量`() {
        val a = JapaneseTts.VoiceCandidate(language = "ja", country = "JP", quality = 400, name = "ja-JP-Standard-A")
        val b = JapaneseTts.VoiceCandidate(language = "ja", country = "JP", quality = 500, name = "ja-JP-Standard-B")
        // 无偏好：选质量高的 B
        assertEquals("ja-JP-Standard-B", JapaneseTts.pickBestJapaneseVoice(listOf(a, b))?.name)
        // 偏好 A：即使质量低也选 A
        assertEquals(
            "ja-JP-Standard-A",
            JapaneseTts.pickBestJapaneseVoice(listOf(a, b), preferredName = "ja-JP-Standard-A")?.name,
        )
        // 偏好不存在：回退自动
        assertEquals(
            "ja-JP-Standard-B",
            JapaneseTts.pickBestJapaneseVoice(listOf(a, b), preferredName = "gone")?.name,
        )
        // 偏好指向未安装 voice：不选它
        val missing = JapaneseTts.VoiceCandidate(
            language = "ja", country = "JP", quality = 900, notInstalled = true, name = "ja-missing",
        )
        assertEquals(
            "ja-JP-Standard-B",
            JapaneseTts.pickBestJapaneseVoice(listOf(a, b, missing), preferredName = "ja-missing")?.name,
        )
    }

    @Test
    fun `voice 显示名可读化`() {
        assertEquals("标准 A", JapaneseTts.voiceDisplayName("ja-JP-Standard-A"))
        assertEquals("标准 D", JapaneseTts.voiceDisplayName("ja-JP-Standard-D"))
        assertEquals("WaveNet B", JapaneseTts.voiceDisplayName("ja-JP-Wavenet-B"))
        assertEquals("Neural2 C", JapaneseTts.voiceDisplayName("ja-JP-Neural2-C"))
        assertEquals("默认", JapaneseTts.voiceDisplayName(""))
        assertEquals("xyz", JapaneseTts.voiceDisplayName("xyz"))
    }

    @Test
    fun `没有本地日语 voice 时才退到网络 voice`() {
        val netJa = JapaneseTts.VoiceCandidate(
            language = "ja", country = "JP", quality = 400, networkRequired = true, name = "ja-net",
        )
        assertEquals("ja-net", JapaneseTts.pickBestJapaneseVoice(listOf(netJa))?.name)
        assertEquals(null, JapaneseTts.pickBestJapaneseVoice(emptyList()))
    }

    @Test
    fun `voice 探测 空列表 仅中文 未下载日语 已安装`() {
        assertEquals(VoiceProbe.UNKNOWN, JapaneseTts.classifyJapaneseVoices(emptyList()))
        assertEquals(
            VoiceProbe.NONE,
            JapaneseTts.classifyJapaneseVoices(
                listOf(JapaneseTts.VoiceCandidate(language = "zh", country = "CN", name = "zh-cn")),
            ),
        )
        assertEquals(
            VoiceProbe.MISSING_DATA,
            JapaneseTts.classifyJapaneseVoices(
                listOf(
                    JapaneseTts.VoiceCandidate(
                        language = "ja", country = "JP", notInstalled = true, name = "ja-missing",
                    ),
                ),
            ),
        )
        assertEquals(
            VoiceProbe.INSTALLED,
            JapaneseTts.classifyJapaneseVoices(
                listOf(
                    JapaneseTts.VoiceCandidate(
                        language = "ja", country = "JP", notInstalled = true, name = "ja-missing",
                    ),
                    JapaneseTts.VoiceCandidate(language = "ja", country = "JP", name = "ja-local"),
                ),
            ),
        )
    }

    @Test
    fun `仅网络日语 voice 视为缺数据 不当作已安装`() {
        assertEquals(
            VoiceProbe.MISSING_DATA,
            JapaneseTts.classifyJapaneseVoices(
                listOf(
                    JapaneseTts.VoiceCandidate(
                        language = "ja",
                        country = "JP",
                        networkRequired = true,
                        name = "ja-jp-net",
                    ),
                ),
            ),
        )
        assertEquals(
            VoiceProbe.INSTALLED,
            JapaneseTts.classifyJapaneseVoices(
                listOf(
                    JapaneseTts.VoiceCandidate(
                        language = "ja",
                        country = "JP",
                        networkRequired = true,
                        name = "ja-jp-net",
                    ),
                    JapaneseTts.VoiceCandidate(language = "ja", country = "JP", name = "ja-local"),
                ),
            ),
        )
    }

    @Test
    fun `发音点击决策 五种场景`() {
        // 初始化中：先照常暂存（就绪后自动播放）
        assertEquals(Action.SPEAK, JapaneseTts.decideAction(State.WAITING, false, false))
        // 引擎正常且日语可用：直接发音
        assertEquals(Action.SPEAK, JapaneseTts.decideAction(State.READY, true, false))
        // 引擎正常但缺日语语音数据：引导下载数据
        assertEquals(Action.GUIDE_VOICE_DATA, JapaneseTts.decideAction(State.READY, false, true))
        // 引擎正常但不支持日语（如中文引擎，读日文只读汉字跳过假名）：引导安装引擎
        assertEquals(Action.GUIDE_ENGINE, JapaneseTts.decideAction(State.READY, false, false))
        // 初始化失败 / 超时：引导安装引擎
        assertEquals(Action.GUIDE_ENGINE, JapaneseTts.decideAction(State.FAILED, false, false))
    }
}
