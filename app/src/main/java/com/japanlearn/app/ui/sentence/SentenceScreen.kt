package com.japanlearn.app.ui.sentence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.japanlearn.app.LocalAppContainer
import com.japanlearn.app.data.breakdown
import com.japanlearn.app.data.local.SentenceEntity
import com.japanlearn.app.ui.components.AppTopBar
import com.japanlearn.app.ui.components.SectionCard
import com.japanlearn.app.ui.components.SkeletonBlock
import com.japanlearn.app.ui.components.TtsButton
import com.japanlearn.app.ui.motion.StaggerIn
import kotlinx.coroutines.flow.first

@Composable
fun SentenceScreen(nav: NavHostController, index: Int) {
    val app = LocalAppContainer.current
    var sentence by remember { mutableStateOf<SentenceEntity?>(null) }

    LaunchedEffect(index) {
        val list = app.content.sentencesAll().first()
        if (list.isEmpty()) return@LaunchedEffect
        val safeIndex = index.coerceIn(0, list.size - 1)
        sentence = list[safeIndex]
    }

    Scaffold(
        topBar = { AppTopBar("每日一句") { nav.popBackStack() } },
    ) { padding ->
        val s = sentence
        if (s == null) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(28.dp), radius = 10)
                SkeletonBlock(Modifier.fillMaxWidth().height(140.dp), radius = 20)
                SkeletonBlock(Modifier.fillMaxWidth().height(180.dp), radius = 20)
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            StaggerIn(0) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        s.scene,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }
            StaggerIn(1) {
                SectionCard {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            s.ja,
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            s.zh,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        TtsButton(s.ja, onSpeak = { app.tts.speak(it) })
                    }
                }
            }
            StaggerIn(2) {
                SectionCard(title = "词汇拆解") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        s.breakdown().forEachIndexed { i, part ->
                            StaggerIn(i % 6) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(part.t, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        part.zh,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
