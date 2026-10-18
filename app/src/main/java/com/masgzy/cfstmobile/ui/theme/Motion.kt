package com.masgzy.cfstmobile.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * M3 Expressive 动效令牌（规范：m3.material.io/styles/motion/easing-and-duration）。
 *
 * 注意：material3 1.4.0 stable 把 MotionScheme / MaterialExpressiveTheme 等
 * Expressive API 标记为 internal（等 1.5 重新公开），因此这里以同参数的
 * 自定义令牌对象承载官方 Expressive 默认值（spring 参数与 ImageToolbox
 * 的 MotionScheme 实现一致，即官方 token）：
 * - 空间动效（位移/尺寸/形状）一律 spring：fast=灵敏(0.6/800)、default=标准(0.8/380)、slow=舒缓(0.8/200)
 * - 效果动效（颜色/透明度）default 用 spring(1.0/1600)（无过冲），fast/slow 用 emphasized 缓动 tween
 * 颜色等连续值走 effects，位移走 spatial（轻微弹性是 Material You「丝滑」手感的关键）。
 */
object CfMotion {
    /** M3 emphasized 缓动曲线（0.2, 0, 0, 1）——tween 类效果动效统一使用 */
    private val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    private val fastSpatial = spring<Float>(dampingRatio = 0.6f, stiffness = 800f)
    private val defaultSpatial = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
    private val slowSpatial = spring<Float>(dampingRatio = 0.8f, stiffness = 200f)
    private val defaultEffects = spring<Float>(dampingRatio = 1f, stiffness = 1600f)
    private val fastEffects = tween<Float>(durationMillis = 300, easing = EmphasizedEasing)
    private val slowEffects = tween<Float>(durationMillis = 500, easing = EmphasizedEasing)

    @Suppress("UNCHECKED_CAST")
    private fun <T> FiniteAnimationSpec<Float>.cast(): FiniteAnimationSpec<T> =
        this as FiniteAnimationSpec<T>

    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = fastSpatial.cast()
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = defaultSpatial.cast()
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = slowSpatial.cast()
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = fastEffects.cast()
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = defaultEffects.cast()
    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = slowEffects.cast()
}
