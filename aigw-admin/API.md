# AI 网关后台 · APP 对接文档

面向 Android APP（`aigw`）开发者：如何把网关接到 `aigw-admin` 后台。

- 服务地址示例：`https://gw-admin.example.com`
- 所有接口返回 JSON，`code = 0` 表示成功
- 内容协商：请求头 `Content-Type: application/json`

---

## 目录

1. [鉴权](#1-鉴权)
2. [错误码](#2-错误码)
3. [接口一览](#3-接口一览)
4. [各接口详细](#4-各接口详细)
5. [心跳响应详解](#5-心跳响应详解)
6. [APP 侧实现要点](#6-app-侧实现要点)
7. [联调自测（curl）](#7-联调自测curl)

---

## 1. 鉴权

所有 `/api/v1/*` 接口都要求 HMAC-SHA256 签名。

### 请求头

| 头 | 必填 | 说明 |
|---|---|---|
| `X-Device-Id` | 是 | 设备唯一 ID，32 位（推荐 UUID 去横线） |
| `X-Signature` | 是 | `hex(hmac_sha256(原始请求体字节, AIGW_HMAC_SECRET))` |
| `X-Timestamp` | 否 | Unix 秒。服务端容差 300 秒，超时判 `sign_expired` |
| `Content-Type` | 是 | `application/json` |

### 签名规则（关键）

**对原始请求体的字节直接做 HMAC，不做任何 JSON 重排或序列化。**

- POST：对发送出去的那段 JSON 字符串（UTF-8 字节）签名
- GET：请求体为空，**对空字符串签名**（即 `hmac_sha256("", secret)`）

低 64 位十六进制，服务端用 `hash_equals` 比较，**大小写不敏感**（服务端会 `strtolower`）。

### Kotlin 实现

```kotlin
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

fun hmacSha256Hex(rawBody: String, secret: String): String {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
    val bytes = mac.doFinal(rawBody.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

// POST
val raw = jsonObject.toString()            // 必须是最终发送的那串
val sig = hmacSha256Hex(raw, secret)

// GET（无 body）
val sigGet = hmacSha256Hex("", secret)
```

### 密钥来源

`AIGW_HMAC_SECRET` 与服务端同值。个人自用项目直接编译进 App 即可（`BuildConfig` 或常量）；
若要更高安全性，可由服务端在注册时下发，APP 存入 `EncryptedSharedPreferences`。

---

## 2. 错误码

| HTTP | code | 含义 | APP 处理建议 |
|---|---|---|---|
| 200 | 0 | 成功 | 正常解析 `data` |
| 400 | 400 | 参数不合法（缺字段/格式错/敏感字段） | 不重试，记日志 |
| 401 | 401 | 缺鉴权头 / 签名错误 / 时间戳过期 | **不重试**，检查密钥与时钟 |
| 404 | 404 | 路径不存在 | 不重试（版本不匹配） |
| 429 | 429 | 触发限流（写请求 10 秒内 > 6 次） | 退避重试 |
| 500 | 500 | 服务端异常 | 退避重试 |

响应体：

```json
{ "code": 401, "msg": "invalid signature" }
```

> 敏感字段被拒时返回 `{"code":400,"msg":"payload rejected"}`，服务端同时写入审计日志 `leak_attempt`。

---

## 3. 接口一览

| 方法 | 路径 | 用途 | 频率 |
|---|---|---|---|
| POST | `/api/v1/register` | 首次启动注册设备 | 仅一次 |
| POST | `/api/v1/heartbeat` | 心跳（**合并下发**开关/公告/版本/配置） | 60~300s |
| GET | `/api/v1/announcements` | 单独拉公告（可选） | 按需 |
| POST | `/api/v1/announcements/read` | 公告已读上报 | 用户读后 |
| GET | `/api/v1/release/check` | 单独查版本（可选） | 启动+每日 |
| POST | `/api/v1/sync/providers` | 供应商状态上报 | 状态变化/心跳 |
| POST | `/api/v1/bugs` | Bug 反馈提交 | 用户触发 |

**推荐只用心跳接口**：它一次返回开关、公告、版本、配置四类数据，能大幅减少请求数。

---

## 4. 各接口详细

### 4.1 设备注册 — `POST /api/v1/register`

首次启动调用一次。同 device_id 重复调用是幂等的（更新指纹）。

**请求体**

```json
{
  "app_version": "1.7.20",
  "version_code": 71,
  "device": {
    "manufacturer": "Xiaomi",
    "model": "23127PN0CC",
    "android_version": "15"
  }
}
```

**响应**

```json
{ "code": 0, "data": { "registered": true } }
```

---

### 4.2 心跳 — `POST /api/v1/heartbeat`

**请求体**

```json
{
  "app_version": "1.7.20",
  "version_code": 71,
  "running": true,
  "port": 8790,
  "pool_summary": {
    "trae":   { "total": 2, "ok": 2 },
    "loomy":  { "total": 1, "ok": 0 },
    "custom:openrouter": { "total": 1, "ok": 1 }
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `app_version` | string | 版本名 |
| `version_code` | int | 用于版本比较与公告过滤 |
| `running` | bool | 网关是否运行中 |
| `port` | int | 监听端口 |
| `pool_summary` | object | 各供应商账号统计，`total`/`ok`；服务端会裁剪非法值 |

**响应**（详见 [第 5 节](#5-心跳响应详解)）

```json
{
  "code": 0,
  "data": {
    "interval_sec": 60,
    "server_time": 1730000000,
    "provider_overrides": [],
    "announcements": [],
    "release": null,
    "config": { "heartbeat_interval_sec": 60 }
  }
}
```

---

### 4.3 公告列表 — `GET /api/v1/announcements?version_code=71`

可选接口。若已用心的跳下发，可不接。

**响应**

```json
{
  "code": 0,
  "data": {
    "announcements": [
      {
        "dedup_key": "2024-11-maintenance",
        "title": "维护通知",
        "body": "今晚 23:00 起进行上游维护，预计 1 小时。",
        "body_hash": "9f2c...（sha256 64 位 hex）",
        "level": "warn",
        "action_label": "查看详情",
        "action_url": "https://example.com/notice"
      }
    ]
  }
}
```

---

### 4.4 公告已读 — `POST /api/v1/announcements/read`

**请求体**

```json
{ "dedup_key": "2024-11-maintenance" }
```

**响应**

```json
{ "code": 0 }
```

同一设备同一 `dedup_key` 重复上报是幂等的（覆盖 `read_at`）。

---

### 4.5 版本检查 — `GET /api/v1/release/check?version_code=71`

可选接口。与心跳下发的判定逻辑完全一致。

**有新版本时**

```json
{
  "code": 0,
  "data": {
    "update": {
      "version": "1.7.21",
      "version_code": 72,
      "changelog": "- 修复 xxx\n- 优化 yyy",
      "apk_url": "https://gw-admin.example.com/apk/ai-gateway-1.7.21.apk",
      "apk_size": 7681628,
      "apk_sha256": "a3f1...（64 位 hex）",
      "force_update": false
    }
  }
}
```

**无更新 / 未命中灰度时**

```json
{ "code": 0, "data": { "update": null } }
```

---

### 4.6 供应商状态上报 — `POST /api/v1/sync/providers`

**请求体**

```json
{
  "providers": [
    {
      "provider_id": "trae",
      "enabled": true,
      "accounts_total": 2,
      "accounts_ok": 2,
      "last_error": "",
      "last_call_at": 1730000000
    }
  ]
}
```

| 字段 | 说明 |
|---|---|
| `provider_id` | 供应商 ID，如 `trae` / `codebuddy` / `loomy` / `custom:<key>` |
| `enabled` | 该供应商当前是否启用 |
| `accounts_total` / `accounts_ok` | 账号总数 / 可用数（服务端裁剪到 0~999，`ok ≤ total`） |
| `last_error` | 最近错误摘要（≤200 字），**不要放凭证** |
| `last_call_at` | 最近调用时间（Unix 秒），服务端裁剪到不超过当前时间 |

**响应**

```json
{ "code": 0, "data": { "accepted": 1 } }
```

#### ⚠️ 严禁上报的字段

服务端会**递归扫描最多 6 层**，命中以下任一 key 就整批拒绝（HTTP 400 + 审计）：

```
token, access_token, refresh_token, id_token, session, cookie, cookies,
authorization, auth, secret, apikey, api_key, password,
uid, user_id, userid, phone, email, nickname, credentials, credential, headers
```

**只上报统计数字与状态，绝不上报账号凭证。**

---

### 4.7 Bug 反馈 — `POST /api/v1/bugs`

**请求体**

```json
{
  "title": "Loomy 登录后倒计时不重置",
  "detail": "登录成功后验证码输入框与倒计时状态未清空。",
  "log_excerpt": "2026-10-24 21:15:19 [INFO] 登录…\n2026-10-24 21:15:20 [ERROR] …",
  "severity": "mid",
  "app_version": "1.7.20"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `title` | 是 | ≤300 字 |
| `detail` | 否 | 用户描述，≤5000 字 |
| `log_excerpt` | 否 | 自动摘取的日志摘要，≤8000 字，**必须脱敏** |
| `severity` | 否 | `low` / `mid` / `high`，默认 `mid` |
| `app_version` | 否 | 版本名 |

**响应**

```json
{ "code": 0, "data": { "id": 12 } }
```

---

## 5. 心跳响应详解

```json
{
  "code": 0,
  "data": {
    "interval_sec": 60,
    "server_time": 1730000000,
    "provider_overrides": [
      { "provider_id": "trae", "enabled": false, "reason": "上游风控" }
    ],
    "announcements": [
      {
        "dedup_key": "2024-11-maintenance",
        "title": "维护通知",
        "body": "……",
        "body_hash": "9f2c...",
        "level": "warn",
        "action_label": null,
        "action_url": null
      }
    ],
    "release": {
      "version": "1.7.21",
      "version_code": 72,
      "changelog": "……",
      "apk_url": "https://……/app.apk",
      "apk_size": 7681628,
      "apk_sha256": "a3f1...",
      "force_update": false
    },
    "config": { "heartbeat_interval_sec": 60 }
  }
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `interval_sec` | int | **下次心跳间隔（秒）**，服务端可远程调整，范围 30~3600 |
| `server_time` | int | 服务器 Unix 秒，可用于校准本地时钟偏差 |
| `provider_overrides` | array | 远程开关覆盖项，**只含被后台显式修改过的**；空数组表示无覆盖 |
| `announcements` | array | 最多 3 条，按 `critical > warn > info` 排序 |
| `release` | object\|null | 有新版本时为对象，否则为 `null` |
| `config` | object | 远程配置，目前含 `heartbeat_interval_sec` |

### 三个必做处理

#### ① 远程开关（D3）— 必须是「可逆软禁用」

```kotlin
// provider_overrides 只下发被后台改过的项
// Engine 现有 API：setProviderEnabled(id, enabled)（写入 settings.disabledProviders）
for (ov in overrides) {
    engine.setProviderEnabled(ov.providerId, ov.enabled)
    if (!ov.enabled) {
        engine.eventLog.warn("远程", "${ov.providerId} 被后台停用：${ov.reason ?: ""}")
    }
}
```

- **软禁用**：`setProviderEnabled(id, false)` 只是把 ID 加进 `settings.disabledProviders`，用户仍可在 UI 手动恢复
- **与凭证失效的硬禁用区分**：`ApiError.Kind.AUTH` 走的是 `pool.setDisabled(...)`（硬禁用，需重新登录），远程开关不改账号池状态
- 未出现在 `provider_overrides` 里的供应商，**保持本地状态不变**（不要重置为启用）

#### ② 公告去重（B2）

```kotlin
val seen = prefs.getStringSet("ann_seen", emptySet())   // 存 "dedup_key:body_hash"
for (a in announcements) {
    val key = "${a.dedupKey}:${a.bodyHash}"
    if (key in seen) continue                            // 已弹过，跳过
    when (a.level) {
        "critical" -> showBlockingDialog(a)              // 阻塞弹窗
        "warn"     -> showBanner(a)                      // 顶部横幅
        else       -> addToNoticeList(a)                 // 列表
    }
    seen += key
}
```

**为什么用 `dedup_key + body_hash`**：同一个 `dedup_key` 内容改了（`body_hash` 变化）会重新弹；内容没变则不重复打扰。

展示后调 `/api/v1/announcements/read` 上报已读（后台统计触达）。

#### ③ 版本更新（C1/C2/C3/C4）

```kotlin
release?.let { r ->
    if (r.versionCode > BuildConfig.VERSION_CODE) {
        if (r.forceUpdate) showForceUpdateDialog(r)      // 阻塞，不可跳过
        else showOptionalUpdateDialog(r)                 // 可稍后
    }
}
```

下载后**必须校验**：

```kotlin
r.apkSha256?.let { expected ->
    val actual = sha256Hex(downloadedFile)
    require(actual.equals(expected, ignoreCase = true)) { "APK 校验失败" }
}
r.apkSize?.let { require(downloadedFile.length() == it.toLong()) { "APK 大小不符" } }
```

`force_update = true` 的两种来源：单版本 `force_update` 标记，或本地 `version_code < 全局最低支持线`。

---

## 6. APP 侧实现要点

### 6.1 首次启动：生成并注册

```kotlin
val prefs = EncryptedSharedPreferences(...)
val deviceId = prefs.getString("device_id", null) ?: run {
    val id = UUID.randomUUID().toString().replace("-", "")   // 32 位
    prefs.edit().putString("device_id", id).apply()
    id
}
// 若从未注册过（prefs 无 registered 标记）→ POST /api/v1/register
```

### 6.2 心跳循环

```kotlin
// 建议放在前台服务里，与网关生命周期一致
var intervalSec = 60
while (running) {
    runCatching {
        val resp = api.heartbeat(buildHeartbeatBody())
        intervalSec = resp.data.intervalSec.coerceIn(30, 3600)   // 用服务端下发值
        applyOverrides(resp.data.providerOverrides)
        showAnnouncements(resp.data.announcements)
        checkRelease(resp.data.release)
    }.onFailure {
        // G1：失败静默，不影响网关；指数退避
        delayMs = min(delayMs * 2, 60_000)
    }
    delay(if (成功) intervalSec * 1000L else delayMs)
}
```

**铁律：上报失败绝不能影响网关主流程。** 网络异常、后台 5xx、超时都要静默吞掉并退避重试。

### 6.3 状态变化时补报供应商

网关内账号失效 / 登录成功 / 手动启停 / 冷却状态变化时，防抖 5 秒后调 `/api/v1/sync/providers`：

```kotlin
private val syncDebounce = 5000L
private var lastSyncAt = 0L
fun scheduleProviderSync() {
    val now = System.currentTimeMillis()
    if (now - lastSyncAt < syncDebounce) return
    lastSyncAt = now
    scope.launch(Dispatchers.IO) { runCatching { api.syncProviders(buildProviders()) } }
}
```

### 6.4 上报数据组装（对照现有 Kotlin 代码）

```kotlin
// 供应商统计：Engine 现有 API 为 providerIds() / pool.accounts(id) / isProviderEnabled(id)
fun buildProviders(): List<ProviderStat> =
    engine.providerIds().map { id ->
        val accounts = engine.pool.accounts(id)
        ProviderStat(
            providerId    = id,
            enabled       = engine.isProviderEnabled(id),
            accountsTotal = accounts.size,
            accountsOk    = accounts.count { it.state.enabled && !it.state.inCooldown() },
            lastError     = accounts.firstNotNullOfOrNull { it.state.lastError }?.take(200) ?: "",
            lastCallAt    = accounts.maxOfOrNull { it.state.lastErrorAt }?.div(1000) ?: 0,
        )
    }
```

### 6.5 Bug 反馈自动带日志

从 `engine.eventLog` 取最近 10 条，**先脱敏**（去掉 token/手机号/账号 ID），再截断到 8000 字：

```kotlin
fun buildLogExcerpt(): String =
    engine.eventLog.snapshot().takeLast(10)
        .joinToString("\n") { "${it.time} [${it.level}] [${it.tag}] ${it.message}" }
        .let { redact(it).take(8000) }
```

---

## 7. 联调自测（curl）

设：

```sh
BASE=https://gw-admin.example.com
SECRET=你的AIGW_HMAC_SECRET
DID=0123456789abcdef0123456789abcdef
```

### 注册

```sh
BODY='{"app_version":"1.7.20","version_code":71,"device":{"manufacturer":"Xiaomi","model":"test","android_version":"15"}}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SECRET" -hex | sed 's/^.*= //')
curl -s -X POST "$BASE/api/v1/register" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: $DID" \
  -H "X-Signature: $SIG" \
  -d "$BODY"
```

### 心跳

```sh
BODY='{"app_version":"1.7.20","version_code":71,"running":true,"port":8790,"pool_summary":{"trae":{"total":1,"ok":1}}}'
SIG=$(printf '%s' "$BODY" | openssl dgst -sha256 -hmac "$SECRET" -hex | sed 's/^.*= //')
curl -s -X POST "$BASE/api/v1/heartbeat" \
  -H "Content-Type: application/json" \
  -H "X-Device-Id: $DID" \
  -H "X-Signature: $SIG" \
  -d "$BODY"
```

### GET（对空串签名）

```sh
SIG=$(printf '' | openssl dgst -sha256 -hmac "$SECRET" -hex | sed 's/^.*= //')
curl -s "$BASE/api/v1/release/check?version_code=71" \
  -H "X-Device-Id: $DID" \
  -H "X-Signature: $SIG"
```

### 常见问题

| 现象 | 原因 |
|---|---|
| `missing auth headers` | 漏了 `X-Device-Id` 或 `X-Signature` |
| `invalid signature` | ①签名用的 body 与发送的不一致（如重新序列化了 JSON）②密钥不对 ③GET 未对空串签名 |
| `signature expired` | 带了 `X-Timestamp` 且偏差 > 300s，检查设备时钟 |
| `payload rejected` | 供应商上报里含禁止字段（token/uid/phone 等），看后台审计日志 |
| `too many requests` | 触发限流，检查是否重复刷心跳 |
