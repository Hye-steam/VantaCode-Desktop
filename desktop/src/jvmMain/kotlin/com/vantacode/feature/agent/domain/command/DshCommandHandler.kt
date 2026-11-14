package com.vantacode.feature.agent.domain.command

import com.vantacode.core.ui.R
import javax.inject.Inject

/**
 * /dsh —— 把任务委托给 DSH（DeepSeek Harness）headless 模式执行，输出回填到当前会话。
 * 需要先在设置 → DSH 插件里安装运行时，并在 DSH 配置页里设置好模型（DeepSeek key 或兼容端点）。
 */
class DshCommandHandler @Inject constructor() : ActionCommandHandler {
    override val name = "dsh"
    override val descriptionRes = R.string.dsh_slash_desc
    override val acceptsArgs = true

    override fun execute(context: SlashCommandContext, args: String) {
        context.runDshTask(args)
    }
}
