package com.subtracks.ui.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.subtracks.ui.theme.MaeksterTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.PixelXL)
class WelcomeScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theWelcomeShowsTheMarkAndLeadsToTheServerForm() {
        var started = false
        composeRule.setContent {
            MaeksterTheme {
                WelcomeScreen(onGetStarted = { started = true })
            }
        }

        composeRule.onNodeWithContentDescription("maekster").assertIsDisplayed()
        composeRule.onNodeWithText("Your music.\nYour rules.").assertIsDisplayed()
        composeRule.onNodeWithText("Get started").performClick()

        assertTrue("Get started should open the server form", started)
    }

    @Test
    @Config(qualifiers = "w320dp-h480dp")
    fun getStartedStaysReachableOnASmallScreenWithLargeText() {
        var started = false
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                MaeksterTheme {
                    WelcomeScreen(onGetStarted = { started = true })
                }
            }
        }

        composeRule.onNodeWithText("Get started").performScrollTo().performClick()

        assertTrue(started)
    }
}
