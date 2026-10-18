package com.masgzy.cfstmobile.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

/**
 * M3 Expressive 动效令牌（对齐 m3.material.io/styles/motion 与 Material 3 1.4.0 官方默认值）：
 * - 空间动效（位移/尺寸/形状）一律 spring：fast=灵敏(0.6/800)、default=标准(0.8/380)、slow=舒缓(0.8/200)
 * - 效果动效（颜色/透明度/模糊）用 spring(1.0/1600) 与带 emphasized 缓动的短 tween
 * 颜色等连续值走 effects（无过冲），位移走 spatial（轻微弹性，Material You「丝滑」手感的关键）。
 * 实现方式与 ImageToolbox 一致：直接实现 MotionScheme 接口，避开 companion 工厂的实验性 API 差异。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val CfMotionScheme: MotionScheme = object : MotionScheme {
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

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = fastSpatial.cast()
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = defaultSpatial.cast()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = slowSpatial.cast()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = fastEffects.cast()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = defaultEffects.cast()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = slowEffects.cast()
}
