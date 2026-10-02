package com.cadence.schedule.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.cadence.schedule.ui.basic.SharedScrollBehavior

/**
 * 为 liquidGlass 的 `layerBackdrop` 生成稳定的 recordKey。
 *
 * ## 为什么需要它
 * backdrop 的约定是「recordKey 为 null 时，每帧重录整棵子树」
 * （见 `LayerBackdropModifier` 的 draw 实现）。而全应用的二级页面此前都只写了
 * `Modifier.liquidGlassLayerBackdrop(backdrop)`，没有传 recordKey——于是设置类页面
 * 在内容基本静止时，仍每帧把整个全屏子树重录一遍。
 *
 * 真机（HyperOS 3）实测这一项的影响：
 * ```
 *                改前             只改偏好设置页后
 * Janky frames   251 (39.84%)     89 (17.32%)
 * 99th           133ms            61ms
 * Slow draw      247              76
 * ≤16ms 快帧     7  (1.1%)        84 (16.3%)
 * ```
 *
 * ## 为什么用滚动状态当 key，而不是「是否正在滚动」
 *
 * 这三个量都只在滚动过程中变化、静止时恒定：
 *  - `state.heightOffset`   —— 折叠进度（0 → heightOffsetLimit）
 *  - `currentHeightPx`      —— 顶栏当前高度
 *  - `postCollapseScrollOffset` —— 折叠后列表自身的滚动偏移
 *
 * 各自的惯性滑动由 `Animatable`/`animateTo` 驱动，因此**惯性阶段也会持续变化**，
 * 玻璃不会冻结。于是不需要 `mustRecord` 也能保证正确性——recordKey 变即重录，
 * 静止即停录，语义天然自洽。
 *
 * 注意：不要把这类"每帧都变"的值（如动画帧时间）放进 key，否则等于没优化；
 * 也不要只放一个恒定的 key，那会让玻璃冻结在旧帧。
 *
 * @param scrollBehavior 该页面的共享滚动行为；为 null 时退化为恒定 key（页面无滚动）。
 */
@Composable
fun rememberLiquidGlassRecordKey(scrollBehavior: SharedScrollBehavior?): Any {
    return remember(
        scrollBehavior,
        scrollBehavior?.currentHeightPx,
        scrollBehavior?.postCollapseScrollOffset,
        scrollBehavior?.state?.heightOffset,
    ) {
        if (scrollBehavior == null) {
            // 无滚动源的页面：内容静止，恒定 key 即可，避免每帧重录
            "static"
        } else {
            listOf(
                scrollBehavior.currentHeightPx,
                scrollBehavior.postCollapseScrollOffset,
                scrollBehavior.state.heightOffset,
            )
        }
    }
}
