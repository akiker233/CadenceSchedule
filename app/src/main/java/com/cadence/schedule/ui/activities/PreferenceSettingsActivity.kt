/** 偏好设置页面 */
package com.cadence.schedule.ui.activities

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.cadence.schedule.ui.basic.CollapsibleTopAppBar
import com.cadence.schedule.ui.basic.LiquidTopBarButton
import com.cadence.schedule.ui.basic.ProgressiveBlurTopBar
import com.cadence.schedule.ui.basic.rememberSharedScrollBehavior
import com.cadence.schedule.ui.utils.applyThemeAwareSystemBars
import com.cadence.schedule.ui.utils.isAppDarkTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.kyant.backdrop.backdrops.layerBackdrop as liquidGlassLayerBackdrop
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.cadence.schedule.ui.theme.CourseScheduleTheme

class PreferenceSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        applyThemeAwareSystemBars()
        setContent {
            CourseScheduleTheme {
            val isDark = isAppDarkTheme()
            LaunchedEffect(isDark) {
                applyThemeAwareSystemBars()
            }
            val backgroundColor = MiuixTheme.colorScheme.surface
            val backdrop = rememberLayerBackdrop {
                drawRect(backgroundColor)
                drawContent()
            }
            val liquidGlassBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
            val scrollBehavior = rememberSharedScrollBehavior()

            // liquidGlass 录制节流。
            //
            // backdrop 的约定是「recordKey == null ⇒ 每帧重录整棵子树」。本页面此前没传
            // recordKey，导致设置页内容基本静止时，仍每帧把整个全屏子树重录一遍——
            // 这是二级页面普遍掉帧的主因（gfxinfo: Slow issue draw commands 占 36%、
            // 快帧占比仅 1.1%，是恒定开销而非抖动）。
            //
            // recordKey 用滚动状态而非「是否正在滚动」：
            //   state.heightOffset / currentHeightPx 随折叠进度变化，
            //   postCollapseScrollOffset 随折叠后的列表滚动变化，
            //   三者在静止时都恒定（实测各自由 Animatable/animateTo 驱动惯性滑动）。
            // 于是滚动时自然重录、空闲时自动停止，无需 mustRecord 也能保证玻璃不冻结。
            // 注意：这里刻意不把滚动值放进组合期的其它判断，只作为 key 使用。
            val liquidGlassRecordKey = remember(
                scrollBehavior,
                scrollBehavior.currentHeightPx,
                scrollBehavior.postCollapseScrollOffset,
                scrollBehavior.state.heightOffset,
            ) {
                listOf(
                    scrollBehavior.currentHeightPx,
                    scrollBehavior.postCollapseScrollOffset,
                    scrollBehavior.state.heightOffset,
                )
            }

            Scaffold(
                topBar = {
                    ProgressiveBlurTopBar(
                        backdrop = liquidGlassBackdrop,
                    ) {
                        CollapsibleTopAppBar(
                            title = "应用偏好设置",
                            largeTitle = "应用偏好设置",
                            modifier = Modifier,
                            scrollBehavior = scrollBehavior,
                            contentPadding = {},
                            startAction = { backdropAlpha, shadowAlpha ->
                                LiquidTopBarButton(
                                    onClick = { finish() },
                                    backdrop = liquidGlassBackdrop,
                                    icon = MiuixIcons.ChevronBackward,
                                    contentDescription = "返回",
                                    performHapticFeedback = false,
                                    iconSize = 25.dp,
                                    iconOffset = DpOffset(x = (-2).dp, y = 0.dp),
                                    backdropAlpha = backdropAlpha,
                                    shadowAlpha = shadowAlpha,
                                )
                            },
                        )
                    }
                }
            ) { _ ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().then(
                            Modifier.liquidGlassLayerBackdrop(
                                backdrop = liquidGlassBackdrop,
                                recordKey = liquidGlassRecordKey,
                            )
                        )
                    ) {
                        PreferenceSettingsScreen(
                            scrollBehavior = scrollBehavior,
                            liquidGlassBackdrop = liquidGlassBackdrop,
                        )
                    }
                }
            }
        
        }
        }
    }
}
