package com.example.phoneagent.ui.designsystem.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1000dp-h600dp-land-mdpi")
class ContentColumnUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun boundsOf(tag: String) = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    @Test
    fun `on a wide screen the column is 640dp wide and centered`() {
        composeRule.setContent {
            Box(Modifier.width(1000.dp).height(600.dp)) { Box(Modifier.contentColumn().fillMaxHeight().testTag("column")) }
        }

        val bounds = boundsOf("column")
        assertEquals(640f, bounds.width, 1f)
        assertEquals(180f, bounds.left, 1f)
    }

    @Test
    fun `on a narrow screen the column uses the whole width`() {
        composeRule.setContent {
            Box(Modifier.width(360.dp).height(600.dp)) { Box(Modifier.contentColumn().fillMaxHeight().testTag("column")) }
        }

        assertEquals(360f, boundsOf("column").width, 1f)
    }

    @Test
    fun `a narrower cap is respected`() {
        composeRule.setContent {
            Box(Modifier.width(1000.dp).height(600.dp)) { Box(Modifier.contentColumn(maxWidth = 480.dp).fillMaxHeight().testTag("column")) }
        }

        val bounds = boundsOf("column")
        assertEquals(480f, bounds.width, 1f)
        assertEquals(260f, bounds.left, 1f)
    }
}
