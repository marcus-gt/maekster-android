package com.subtracks.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.subtracks.MaeksterBrand
import com.subtracks.R
import com.subtracks.ui.theme.MaeksterControlSize
import com.subtracks.ui.theme.MaeksterSpacing
import com.subtracks.ui.theme.MaeksterTheme

private const val MARK_ASPECT_RATIO = 947f / 434f
private const val GLOW_ALPHA = 0.16f

/**
 * The first screen without a server: the brand mark, what maekster is for, and the way in. It scrolls when the
 * text is large, so the button is always reachable.
 */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = MaeksterSpacing.xxxl, vertical = MaeksterSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(MaeksterSpacing.xl))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrandMarkHero()
                Spacer(Modifier.height(MaeksterSpacing.xxxxl))
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaeksterTheme.text.heroTitle,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(MaeksterSpacing.m))
                Text(
                    text = stringResource(R.string.onboarding_body),
                    style = MaeksterTheme.text.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth().heightIn(min = MaeksterControlSize.large).padding(top = MaeksterSpacing.xxxl),
            ) {
                Text(stringResource(R.string.onboarding_get_started))
            }
        }
    }
}

/** The mark over a soft accent glow. */
@Composable
private fun BrandMarkHero() {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(300.dp)
                .background(
                    Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = GLOW_ALPHA), Color.Transparent)),
                ),
        )
        Image(
            painter = painterResource(R.drawable.ic_maekster_mark),
            contentDescription = MaeksterBrand.NAME,
            modifier = Modifier.width(220.dp).aspectRatio(MARK_ASPECT_RATIO),
        )
    }
}
