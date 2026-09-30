package com.tishan.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class MainActivityUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun managePageExposesImportAndResourceManagementActions() {
        composeRule.onNodeWithText("管理").performClick()
        composeRule.onNodeWithText("导入题库").assertIsDisplayed()
        composeRule.onNodeWithText("导入试卷").assertIsDisplayed()
        composeRule.onNodeWithText("已导入资源").assertIsDisplayed()
    }

    @Test
    fun settingsPageExposesBackupAppearanceAndAbout() {
        composeRule.onNodeWithText("设置").performClick()
        composeRule.onNodeWithText("外观").assertIsDisplayed()
        assertTrue(
            composeRule.onNodeWithText("外观").fetchSemanticsNode().boundsInRoot.top <
                composeRule.onNodeWithText("备份").fetchSemanticsNode().boundsInRoot.top,
        )
        composeRule.onNodeWithText("关于").assertIsDisplayed()
        composeRule.onNodeWithText("fl1ntf1sh").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("备份与恢复").performScrollTo().performClick()
        composeRule.onNodeWithText("备份内容").assertIsDisplayed()
        composeRule.onNodeWithText("导出 .ts 备份").assertIsDisplayed()
        composeRule.onNodeWithText("选择 .ts 文件恢复").assertIsDisplayed()
        composeRule.onNodeWithText("全量").assertIsDisplayed()
    }

    @Test
    fun fontPickerKeepsDialogOpenWhileTryingScalesAndClosesOnDone() {
        composeRule.onNodeWithContentDescription("设置导航").performClick()
        listOf("大", "小", "标准", "大").forEach { scale ->
            composeRule.onNodeWithText("字体大小").performClick()
            composeRule.onNodeWithText("阅读预览").assertIsDisplayed()
            composeRule.onNodeWithText(scale).performClick()
            // 点选字号只更新预览，弹窗保持打开，点「完成」才应用并关闭
            composeRule.onNodeWithText("阅读预览").assertIsDisplayed()
            composeRule.onNodeWithText("完成").performClick()
            composeRule.onNodeWithText("阅读预览").assertDoesNotExist()
        }
    }

    @Test
    fun practicePageExposesLibrarySelection() {
        composeRule.onNodeWithText("我的题库").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("搜索题库").performClick()
        composeRule.onNodeWithText("搜索题库名称").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭题库搜索").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("关闭题库搜索").performClick()
        composeRule.onNodeWithText("搜索题库名称").assertDoesNotExist()
    }

    @Test
    fun importShortcutReturnsToPracticeFromBottomNavigation() {
        if (composeRule.onAllNodesWithText("去导入题库").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("去导入题库").performClick()
        } else {
            composeRule.onNodeWithContentDescription("管理导航").performClick()
        }
        composeRule.onNodeWithText("已导入资源").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("刷题导航").performClick()
        composeRule.onNodeWithText("我的题库").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("刷题导航").assertIsSelected()
    }

    @Test
    fun reviewPageExposesReviewModes() {
        composeRule.onNodeWithText("复习").performClick()
        composeRule.onAllNodesWithText("收藏", substring = true)[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("错题", substring = true)[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("背题", substring = true)[0].assertIsDisplayed()
        composeRule.onNodeWithText("温故知新，慢慢扎根").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("复习设置").assertDoesNotExist()
        composeRule.onNodeWithText("按题库与题型").assertIsDisplayed()
        composeRule.onNodeWithText("顺序").performClick()
        composeRule.onNodeWithText("随机").assertIsDisplayed()
        composeRule.onNodeWithText("随机").performClick()
        composeRule.onNodeWithText("随机").assertIsDisplayed()
    }

    @Test
    fun mockExamIsAnIndependentBottomDestination() {
        composeRule.onNodeWithText("模拟").performClick()
        composeRule.onNodeWithText("可开始").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationExposesFiveDestinations() {
        listOf("刷题", "模拟", "复习", "管理", "设置").forEach { label ->
            composeRule.onNodeWithContentDescription("${label}导航").assertIsDisplayed()
        }
    }

    @Test
    fun bottomNavigationMarksCurrentDestinationAsSelected() {
        composeRule.onNodeWithContentDescription("刷题导航").assertIsSelected()
        composeRule.onNodeWithContentDescription("模拟导航").performClick()
        composeRule.onNodeWithContentDescription("模拟导航").assertIsSelected()
        composeRule.onNodeWithContentDescription("刷题导航").assertIsNotSelected()
    }
}
