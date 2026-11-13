/*
 * VantaCode Desktop — 桌宠表情状态机。由 agent 运行状态驱动：
 * 空闲 / 思考 / 输出 / 完工 / 卡住。
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.feature.pet.presentation

/** 桌宠表情。气泡文案在 Strings 里（pet_bubble_*），索引与 [name] 顺序无关。 */
enum class PetExpression {
    IDLE,
    LOOKING,
    STREAMING,
    PLEASED,
    DOWNCAST,
}
