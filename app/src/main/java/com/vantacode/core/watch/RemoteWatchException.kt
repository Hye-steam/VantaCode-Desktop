package com.vantacode.core.watch

/** 远程文件监听的失败原因（条目数/深度超限等），[code] 供上层分类提示。 */
class RemoteWatchException(val code: String, message: String) : Exception(message)
