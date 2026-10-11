package com.example.phoneagent.ui.login

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.phoneagent.R
import com.example.phoneagent.ui.designsystem.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private fun Context.text(id: Int) = getString(id)

abstract class LoginLayoutBase {
    @get:Rule
    val composeRule = createComposeRule()

    protected val context: Context = ApplicationProvider.getApplicationContext()

    protected fun show() {
        composeRule.setContent {
            AppTheme(useDynamicColor = false) { LoginScreen(state = LoginUiState(), form = LoginFormState(), onEvent = {}) }
        }
    }

    protected fun title() = composeRule.onNodeWithText(context.text(R.string.login_title)).fetchSemanticsNode().boundsInRoot

    protected fun signIn() = composeRule.onNodeWithText(context.text(R.string.login_openai)).fetchSemanticsNode().boundsInRoot
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h360dp-land-xhdpi")
class LoginLandscapeUiTest : LoginLayoutBase() {
    @Test
    fun `in landscape the sign-in buttons are beside the introduction, not under it`() {
        show()

        val title = title()
        val signIn = signIn()

        assertTrue("sign-in $signIn must be to the right of the title $title", signIn.left >= title.right)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class LoginPortraitUiTest : LoginLayoutBase() {
    @Test
    fun `in portrait the sign-in buttons are below the introduction`() {
        show()

        val title = title()
        val signIn = signIn()

        assertTrue("sign-in $signIn must be below the title $title", signIn.top >= title.bottom)
    }
}
