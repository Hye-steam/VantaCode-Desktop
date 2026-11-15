package com.vantacode.feature.settings.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vantacode.feature.settings.data.repository.SkillInstallResult
import com.vantacode.feature.settings.data.repository.SkillMarketEntry
import com.vantacode.feature.settings.data.repository.SkillMarketRegistry
import com.vantacode.feature.settings.data.repository.SkillMarketRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 安装状态。 */
sealed class InstallState {
    data object Idle : InstallState()
    data object Loading : InstallState()
    data class Success(val names: List<String>) : InstallState()
    data class Error(val message: String) : InstallState()
}

/**
 * 技能市场 ViewModel：浏览注册表、搜索、安装、管理本地技能。
 */
@HiltViewModel
class SkillMarketViewModel @Inject constructor(
    application: Application,
    private val repository: SkillMarketRepository
) : AndroidViewModel(application) {

    private val _marketSkills = MutableStateFlow<List<SkillMarketEntry>>(emptyList())
    val marketSkills: StateFlow<List<SkillMarketEntry>> = _marketSkills.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    val installState: StateFlow<InstallState> = _installState.asStateFlow()

    private val _installedSkills = MutableStateFlow<List<String>>(emptyList())
    val installedSkills: StateFlow<List<String>> = _installedSkills.asStateFlow()

    private val _remoteRegistryUrl = MutableStateFlow("")
    val remoteRegistryUrl: StateFlow<String> = _remoteRegistryUrl.asStateFlow()

    init {
        loadBuiltinRegistry()
        refreshInstalled()
    }

    /** 加载内置市场注册表。 */
    fun loadBuiltinRegistry() {
        val registry = repository.loadBuiltinRegistry()
        _marketSkills.value = registry.skills
    }

    /** 从远程 URL 加载注册表（覆盖内置列表）。 */
    fun loadRemoteRegistry(url: String) {
        _remoteRegistryUrl.value = url
        viewModelScope.launch {
            val registry = repository.loadRegistry(url)
            if (registry.skills.isNotEmpty()) {
                _marketSkills.value = registry.skills
            }
        }
    }

    /** 搜索过滤。 */
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /** 过滤后的技能列表。 */
    fun filteredSkills(): List<SkillMarketEntry> {
        val query = _searchQuery.value.trim().lowercase()
        val all = _marketSkills.value
        return if (query.isEmpty()) {
            all
        } else {
            all.filter {
                it.name.lowercase().contains(query) ||
                    it.description.lowercase().contains(query) ||
                    it.tags.any { tag -> tag.lowercase().contains(query) }
            }
        }
    }

    /** 从市场条目安装。 */
    fun installFromMarket(entry: SkillMarketEntry) {
        if (entry.downloadUrl.isBlank()) {
            _installState.value = InstallState.Error("该技能没有下载地址")
            return
        }
        viewModelScope.launch {
            _installState.value = InstallState.Loading
            _installState.value = when (val result = repository.installFromUrl(entry.downloadUrl)) {
                is SkillInstallResult.Success -> {
                    refreshInstalled()
                    InstallState.Success(result.skillNames)
                }
                is SkillInstallResult.Failure -> InstallState.Error(result.reason)
            }
        }
    }

    /** 从 URL 安装（用户手动输入）。 */
    fun installFromUrl(url: String) {
        if (url.isBlank()) {
            _installState.value = InstallState.Error("请输入有效的 URL")
            return
        }
        viewModelScope.launch {
            _installState.value = InstallState.Loading
            _installState.value = when (val result = repository.installFromUrl(url)) {
                is SkillInstallResult.Success -> {
                    refreshInstalled()
                    InstallState.Success(result.skillNames)
                }
                is SkillInstallResult.Failure -> InstallState.Error(result.reason)
            }
        }
    }

    /** 删除已安装技能。 */
    fun deleteSkill(name: String) {
        viewModelScope.launch {
            repository.deleteSkill(name)
            refreshInstalled()
        }
    }

    /** 读取技能内容预览。 */
    fun readSkillContent(name: String): String? = repository.readSkillContent(name)

    /** 刷新已安装列表。 */
    fun refreshInstalled() {
        _installedSkills.value = repository.listInstalled()
    }

    /** 清除安装状态（用户点掉提示后）。 */
    fun clearInstallState() {
        _installState.value = InstallState.Idle
    }
}
