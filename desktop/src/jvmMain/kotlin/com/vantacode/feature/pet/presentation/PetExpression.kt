/*
 * VantaCode Desktop — 桌宠表情状态机。由 agent 运行状态驱动：
 * 空闲 / 思考 / 输出 / 完工 / 卡住。
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.feature.pet.presentation

import com.vantacode.core.ui.Strings
import com.vantacode.feature.agent.presentation.AgentUIState

/** 桌宠表情。气泡文案在 Strings 里（pet_bubble_*），索引与 [name] 顺序无关。 */
enum class PetExpression {
    IDLE,
    LOOKING,
    STREAMING,
    PLEASED,
    DOWNCAST,
}

/** agent 状态 → 表情。 */
fun expressionOf(state: AgentUIState): PetExpression = when (state) {
    AgentUIState.Idle -> PetExpression.IDLE
    AgentUIState.Loading -> PetExpression.LOOKING
    AgentUIState.Streaming -> PetExpression.STREAMING
    is AgentUIState.Result -> PetExpression.PLEASED
    is AgentUIState.Error -> PetExpression.DOWNCAST
}

/** 表情 → 气泡文案（Strings 已注册 pet_bubble_* 双语）。 */
fun bubbleOf(expression: PetExpression): String = when (expression) {
    PetExpression.IDLE -> Strings.pet_bubble_idle
    PetExpression.LOOKING -> Strings.pet_bubble_looking
    PetExpression.STREAMING -> Strings.pet_bubble_streaming
    PetExpression.PLEASED -> Strings.pet_bubble_pleased
    PetExpression.DOWNCAST -> Strings.pet_bubble_downcast
}
