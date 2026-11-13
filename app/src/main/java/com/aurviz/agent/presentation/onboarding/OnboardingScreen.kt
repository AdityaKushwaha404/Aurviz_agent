package com.aurviz.agent.presentation.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aurviz.agent.presentation.components.AurvizLogo
import com.aurviz.agent.presentation.components.CenteredScroll
import kotlinx.coroutines.launch

/** First-run walkthrough. The last page lets evaluators start instantly with the demo mailbox. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: (source: String) -> Unit) {
    val pages = 3
    val pagerState = rememberPagerState(pageCount = { pages })
    val scope = rememberCoroutineScope()

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (pagerState.currentPage < pages - 1) {
                    TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(pages - 1) } }) { Text("Skip") }
                } else Spacer(modifier = Modifier.height(48.dp))
            }

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> HeroPage()
                    1 -> FeaturesPage()
                    else -> StartPage(onFinish)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages) { index ->
                    val color = animateColorAsState(
                        if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        label = "dot"
                    )
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(if (pagerState.currentPage == index) 22.dp else 8.dp, 8.dp)
                            .background(color.value, CircleShape)
                    )
                }
            }

            if (pagerState.currentPage < pages - 1) {
                Button(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("Continue") }
            } else {
                Spacer(modifier = Modifier.height(52.dp))
            }
        }
    }
}

@Composable
private fun HeroPage() {
    CenteredScroll(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AurvizLogo(size = 120.dp)
        Spacer(modifier = Modifier.height(32.dp))
        Text("Meet Aurviz", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "A personal AI agent that listens, remembers, plans and gets things done on your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeaturesPage() {
    CenteredScroll(modifier = Modifier.fillMaxSize()) {
        Text("Talk or type. Aurviz acts.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(20.dp))
        FeatureRow(Icons.Default.Email, "Email assistant", "Search, summarize and draft mail. It only sends after you approve.")
        FeatureRow(Icons.Default.Psychology, "Personal memory", "Remembers your preferences and uses them in later answers.")
        FeatureRow(Icons.Default.Schedule, "Background tasks", "\"Every morning at 9, summarize my inbox\" keeps running when the app is closed.")
        FeatureRow(Icons.Default.Language, "Web browsing", "Open sites, read pages and summarize them with AI.")
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(46.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StartPage(onFinish: (String) -> Unit) {
    CenteredScroll(modifier = Modifier.fillMaxSize()) {
        Text("Choose a mailbox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Pick how Aurviz reads and sends email. You can change this later in Settings, Mailbox.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))

        // 1. Test account
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Test Gmail account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primary) {
                        Text(
                            "For reviewers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "A real Gmail account made for testing. The next screen shows its email, password and the sign-in steps.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(onClick = { onFinish("TEST") }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("Show me the sign-in steps")
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        // 2. Own Gmail
        OutlinedButton(onClick = { onFinish("OWN") }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Use my own Gmail")
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 3. Demo mailbox
        OutlinedButton(onClick = { onFinish("DEMO") }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Use the offline demo mailbox")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "The demo mailbox needs no sign-in and never sends anything to a real address.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}
