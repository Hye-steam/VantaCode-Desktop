/*
 * VantaCode Desktop — 供全局 overlay（AWT 线程）读取的 agent 状态。
 * Copyright (C) 2026 VantaCode contributors. GPL-3.0.
 */
package com.vantacode.feature.pet.data

import com.vantacode.feature.agent.presentation.AgentUIState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** agent 状态桥：组合树里 agentState 变了，写到这里，overlay 读。 */
object PetRuntimeState {
    private val _state = MutableStateFlow<AgentUIState>(AgentUIState.Idle)
    val state: StateFlow<AgentUIState> = _state.asStateFlow()

    fun update(state: AgentUIState) {
        _state.value = state
    }
}
