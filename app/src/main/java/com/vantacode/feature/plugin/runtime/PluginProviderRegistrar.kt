package com.vantacode.feature.plugin.runtime

import com.vantacode.feature.plugin.domain.PluginManager
import com.vantacode.feature.settings.domain.model.AIProviderConfig
import com.vantacode.feature.settings.domain.model.ProviderType
import com.vantacode.feature.settings.domain.repository.AIProviderRepository
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 把启用插件声明的提供商注册成普通提供商记录，插件停用或卸载时删除。
 *
 * 注册后的条目与用户自建提供商完全同权：可改名、改地址、改 Key、拉取与选择模型。
 * 因此只在缺失时插入，不覆盖已有条目，避免把使用者的修改回滚成清单默认值。
 */
@Singleton
class PluginProviderRegistrar @Inject constructor(
    private val pluginManager: PluginManager,
    private val providerRepository: AIProviderRepository
) {

    /** 与插件当前清单对齐：新增缺失的提供商，移除不再声明的提供商。 */
    suspend fun sync(pluginId: String) {
        pluginManager.ensureLoaded()
        val plugin = pluginManager.plugins.value.firstOrNull { it.id == pluginId }
        val providers = plugin?.takeIf { it.isActive }?.content?.providers.orEmpty()
        val existing = pluginProviderIds(pluginId)
        val wanted = providers.map { PluginProviderIds.idOf(pluginId, it.localId) }.toSet()

        val stale = existing.filterNot { it in wanted }
        if (stale.isNotEmpty()) providerRepository.deleteProviders(stale)

        val languageTag = Locale.getDefault().toLanguageTag()
        providers
            .filter { PluginProviderIds.idOf(pluginId, it.localId) !in existing }
            .forEach { provider ->
                providerRepository.saveProvider(
                    AIProviderConfig(
                        id = PluginProviderIds.idOf(pluginId, provider.localId),
                        name = provider.name.resolve(languageTag),
                        type = ProviderType.OPENAI,
                        apiKey = PLACEHOLDER_API_KEY,
                        baseUrl = provider.baseUrl,
                        defaultModel = ""
                    )
                )
            }
    }

    /** 插件停用或卸载：移除它注册的全部提供商记录。 */
    suspend fun removeAll(pluginId: String) {
        val ids = pluginProviderIds(pluginId)
        if (ids.isNotEmpty()) providerRepository.deleteProviders(ids)
    }

    /**
     * 把插件拉到的可用模型写进对应供应商。
     *
     * 只动 models / defaultModel 两项：地址、Key、排序、自定义头都是用户可能改过的，
     * 整条覆盖会把使用者的调整回滚成清单默认值。
     * 供应商不存在时跳过（说明插件没声明这一家）。
     */
    suspend fun syncModels(pluginId: String, providerId: String, models: List<String>): Int {
        val globalId = PluginProviderIds.idOf(pluginId, providerId)
        val all = providerRepository.getAllProviders().first()
        val target = all.firstOrNull { it.id == globalId } ?: return 0
        if (models.isEmpty()) return 0
        val cleaned = models.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (cleaned.isEmpty()) return 0
        // 默认模型只在当前为空或已不在新列表里时才改，不打断用户已选的
        val keepDefault = target.defaultModel.takeIf { it.isNotBlank() && it in cleaned }
        providerRepository.saveProvider(
            target.copy(
                models = cleaned,
                defaultModel = keepDefault ?: cleaned.first()
            )
        )
        return cleaned.size
    }

    private suspend fun pluginProviderIds(pluginId: String): List<String> =
        providerRepository.getAllProviders().first()
            .map { it.id }
            .filter { PluginProviderIds.pluginIdOf(it) == pluginId }

    companion object {
        /** 插件服务自行处理上游凭据；占位值只用于通过宿主适配器的 Key 检查。 */
        const val PLACEHOLDER_API_KEY = "plugin"
    }
}
