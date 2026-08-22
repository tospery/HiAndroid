package com.tospery.suite.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ConfirmActionDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun destructiveActionDisplaysProvidedContentAndInvokesCallbacks() {
        var confirmedCount = 0
        var dismissedCount = 0

        composeTestRule.setContent {
            MaterialTheme {
                ConfirmActionDialog(
                    title = "确认退出登录？",
                    message = "本地登录状态将被清除。",
                    confirmText = "退出登录",
                    dismissText = "取消",
                    onConfirm = { confirmedCount++ },
                    onDismiss = { dismissedCount++ },
                    confirmActionStyle = ConfirmActionStyle.DESTRUCTIVE,
                )
            }
        }

        composeTestRule.onNodeWithText("确认退出登录？").assertIsDisplayed()
        composeTestRule.onNodeWithText("本地登录状态将被清除。").assertIsDisplayed()
        composeTestRule.onNodeWithText("取消").performClick()
        composeTestRule.onNodeWithText("退出登录").performClick()

        assertEquals(1, dismissedCount)
        assertEquals(1, confirmedCount)
    }

    @Test
    fun supportingContentIsDisplayedAndActionsCanBeDisabled() {
        composeTestRule.setContent {
            MaterialTheme {
                ConfirmActionDialogContent(
                    title = "停止生成并返回？",
                    message = "停止后仍会按当前进度结算 Token。",
                    confirmText = "正在停止…",
                    dismissText = "继续查看",
                    onConfirm = {},
                    onDismiss = {},
                    modifier = Modifier.width(360.dp),
                    confirmEnabled = false,
                    dismissEnabled = false,
                    supportingContent = {
                        Text(text = "停止请求提交失败，当前生成仍在继续。")
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("停止请求提交失败，当前生成仍在继续。")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("正在停止…").assertIsNotEnabled()
        composeTestRule.onNodeWithText("继续查看").assertIsNotEnabled()
    }

    @Test
    fun singleConfirmActionIsTheOnlyButtonAndIsHorizontallyCentered() {
        composeTestRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ConfirmActionDialogContent(
                        title = "发现新版本1.1.0",
                        message = "修复已知问题",
                        confirmText = "立即更新",
                        dismissText = null,
                        onConfirm = {},
                        onDismiss = {},
                        modifier = Modifier.width(360.dp),
                    )
                }
            }
        }

        val rootBounds = composeTestRule.onRoot().getUnclippedBoundsInRoot()
        val confirmBounds =
            composeTestRule
                .onNode(hasClickAction() and hasText("立即更新"))
                .getUnclippedBoundsInRoot()
        val rootCenterX = (rootBounds.left.value + rootBounds.right.value) / 2f
        val confirmCenterX = (confirmBounds.left.value + confirmBounds.right.value) / 2f

        assertEquals(rootCenterX, confirmCenterX, 1f)
    }

    @Test
    fun messageBeyondTenLinesUsesATenLineScrollableViewport() {
        val message = (1..12).joinToString(separator = "\n") { line -> "第 $line 行" }

        composeTestRule.setContent {
            MaterialTheme(
                typography =
                    Typography(
                        bodyMedium = TextStyle(fontSize = 8.sp, lineHeight = 10.sp),
                    ),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ConfirmActionDialogContent(
                        title = "发现新版本1.1.0",
                        message = message,
                        confirmText = "立即更新",
                        dismissText = "稍后提醒",
                        onConfirm = {},
                        onDismiss = {},
                        messageMaxVisibleLines = 10,
                        modifier = Modifier.width(360.dp),
                    )
                }
            }
        }

        val messageViewport = composeTestRule.onNode(hasScrollAction())
        val viewportBounds = messageViewport.getUnclippedBoundsInRoot()
        assertEquals(
            100f,
            viewportBounds.bottom.value - viewportBounds.top.value,
            1f,
        )
        val initialScroll =
            messageViewport.fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange,
            ].value()

        messageViewport.performTouchInput { swipeUp() }
        composeTestRule.waitForIdle()

        val scrolled =
            messageViewport.fetchSemanticsNode().config[
                androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange,
            ].value()
        assertTrue(scrolled > initialScroll)
    }
}
