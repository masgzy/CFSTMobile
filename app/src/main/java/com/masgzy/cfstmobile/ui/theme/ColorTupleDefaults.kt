/*
 * 简单变体预设 —— 照抄 ImageToolbox
 * core/ui/widget/color_picker/ColorTupleDefaults.kt 的 defaultColorTuples：
 * 16 个预设主色原样保留，配套的辅助/第三/表面/中性变体/错误角色
 * 全部由调色引擎的 calculate*Color 公式派生（与 ImageToolbox 相同）。
 * 额外在队首加入 Cloudflare 蓝作为应用默认种子。
 */

package com.masgzy.cfstmobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

object ColorTupleDefaults {

    /** 默认种子：Cloudflare 品牌蓝（原品牌回退方案主色）。 */
    const val DEFAULT_SEED: Long = 0xFF0B57D0

    /**
     * ImageToolbox ColorTupleDefaults.defaultColorTuples 的 16 个预设色，
     * 顺序与色值原样保留。
     */
    private val defaultPrimaryColors = listOf(
        0xFFf8130d,
        0xFF7a000b,
        0xFF8a3a00,
        0xFFff7900,
        0xFFfcf721,
        0xFF88dd20,
        0xFF16B16E,
        0xFF01a0a3,
        0xFF005FFF,
        0xFFfa64e1,
        0xFFd7036a,
        0xFFdb94fe,
        0xFF7b2bec,
        0xFF022b6d,
        0xFFFFFFFF,
        0xFF000000,
    )

    /** 与预设一一对应的中文名（队首为默认种子，不计入 ImageToolbox 16 色）。 */
    private val names = listOf(
        "云蓝", "绯红", "酒红", "赭石", "亮橙", "柠檬黄", "草绿", "翡翠", "青碧",
        "蔚蓝", "粉红", "玫红", "兰紫", "紫罗兰", "藏青", "纯白", "纯黑",
    )

    /** 主色 -> 由同一套公式派生六角色（与 ImageToolbox defaultColorTuples 一致）。 */
    private fun tupleOf(argb: Long): ColorTuple {
        val primary = Color(argb.toInt())
        return ColorTuple(
            primary = primary,
            secondary = Color(primary.calculateSecondaryColor()),
            tertiary = Color(primary.calculateTertiaryColor()),
            surface = Color(primary.calculateSurfaceColor()),
            neutralVariant = Color(primary.calculateNeutralVariantColor()),
        )
    }

    /** 简单变体预设（"云蓝"即默认 Cloudflare 蓝，其余 16 个照抄 ImageToolbox）。 */
    val presets: List<Pair<String, ColorTuple>> by lazy {
        names.zip(listOf(DEFAULT_SEED) + defaultPrimaryColors).map { (name, argb) ->
            name to tupleOf(argb)
        }
    }

    /** 默认配色种子：Cloudflare 蓝，其余角色按同一公式派生。 */
    val defaultTuple: ColorTuple by lazy { tupleOf(DEFAULT_SEED) }
}
