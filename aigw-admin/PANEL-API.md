# 管理面板 API 文档

面向前端开发者 / 二次开发：`/panel/api/*` 的完整接口约定。

- 鉴权方式：**Session Cookie**（用户在 `/panel/` 登录后自动携带）
- 所有响应统一为 `{"code":0,"data":...}` 或 `{"code":4xx,"msg":"..."}`
- 请求体为 JSON，`Content-Type: application/json`
- 未登录时一律返回 `401 login required`，前端应跳回登录页

> APP 端接口（`/api/v1/*`，HMAC 签名）见 [API.md](API.md)。

---

## 目录

1. [鉴权与初始化](#1-鉴权与初始化)
2. [概览](#2-概览)
3. [设备](#3-设备)
4. [公告](#4-公告)
5. [版本](#5-版本)
6. [反馈](#6-反馈)
7. [全局配置](#7-全局配置)
8. [错误处理约定](#8-错误处理约定)

---

## 1. 鉴权与初始化

### 1.1 查询是否已初始化 — `GET /panel/api/init-status`

**无需登录。** 用于判断是否显示「创建管理员」页面。

```json
{ "code": 0, "data": { "needs_init": true } }
```

`needs_init = true` 表示 `admins` 表为空，应展示初始化页面。

---

### 1.2 首次创建管理员 — `POST /panel/api/init`

**无需登录，但仅当 `admins` 表为空时可用。**

请求：

```json
{ "username": "admin", "password": "至少10位密码" }
```

| 字段 | 规则 |
|---|---|
| `username` | 3-50 位，仅字母/数字/下划线 |
| `password` | 至少 10 位 |

成功（同时自动登录）：

```json
{ "code": 0, "data": { "admin": "admin" } }
```

失败：

| code | 场景 |
|---|---|
| 400 | 用户名格式不合规 / 密码不足 10 位 |
| 403 | 已完成初始化（`already initialized`） |
| 429 | 同 IP 5 分钟内超过 5 次尝试 |

> 安全设计：一旦存在管理员，此接口永久关闭，避免有人抢先建号。

---

### 1.3 登录 — `POST /panel/api/login`

```json
{ "username": "admin", "password": "你的密码" }
```

成功：

```json
{ "code": 0, "data": { "admin": "admin" } }
```

失败：

| code | 场景 |
|---|---|
| 400 | 缺少用户名或密码 |
| 401 | 用户名或密码错误 |
| 429 | 同 IP 60 秒内超过 10 次尝试 |

登录成功后设置 Session Cookie（`HttpOnly` + `SameSite=Lax` + HTTPS 下 `Secure`），
服务端会 `session_regenerate_id(true)` 防会话固定。

---

### 1.4 登出 — `POST /panel/api/logout`

```json
{ "code": 0 }
```

清除 Session 并销毁 Cookie。

---

### 1.5 当前登录者 — `GET /panel/api/whoami`

```json
{ "code": 0, "data": { "admin_id": 1 } }
```

---

### 会话超时

空闲超过 `admin_session_idle` 秒（默认 **7200 秒 = 2 小时**）后失效，返回 401 需重新登录。
每次请求都会刷新活跃时间。配置项在 `config.php`，设为 `0` 表示不超时（不建议）。

---

## 2. 概览

### `GET /panel/api/stats`

```json
{
  "code": 0,
  "data": {
    "devices_total": 12,
    "devices_online": 5,
    "devices_silent": 2,
    "devices_offline": 5,
    "bugs_open": 3
  }
}
```

| 字段 | 说明 |
|---|---|
| `devices_total` | 设备总数 |
| `devices_online` | 5 分钟内有心跳 |
| `devices_silent` | 网关自报运行中，但心跳超过 6 小时未更新 |
| `devices_offline` | 其余（网关已停止或长期无心跳） |
| `bugs_open` | 未解决反馈数（`status != 'done'`） |

> 三态互斥：`online + silent + offline = total`

---

## 3. 设备

### 3.1 设备列表 — `GET /panel/api/devices`

返回最近 500 台，按最后心跳倒序。

```json
{
  "code": 0,
  "data": {
    "devices": [
      {
        "device_id": "0123456789abcdef0123456789abcdef",
        "app_version": "1.7.20",
        "version_code": 71,
        "running": true,
        "port": 8790,
        "pool_summary": { "trae": { "total": 2, "ok": 2 } },
        "manufacturer": "Xiaomi",
        "model": "23127PN0CC",
        "android_version": "15",
        "first_seen_at": "2026-10-20 08:00:00",
        "last_seen_at": "2026-10-25 06:30:00",
        "state": "online",
        "alert": false
      }
    ]
  }
}
```

| 字段 | 说明 |
|---|---|
| `state` | `online` / `silent` / `offline`（服务端按心跳时间判定） |
| `alert` | `running=true` 但不在线 → true，即异常设备 |
| `pool_summary` | 各供应商账号统计快照 |

判定阈值：

| 状态 | 条件 |
|---|---|
| `online` | 最后心跳 ≤ 5 分钟 |
| `silent` | 5 分钟 < 最后心跳 ≤ 6 小时 |
| `offline` | 最后心跳 > 6 小时，或从未上报 |

---

### 3.2 设备详情 — `GET /panel/api/device?device_id=xxx`

返回单台设备完整信息，含供应商同步快照与远程开关。

```json
{
  "code": 0,
  "data": {
    "device_id": "0123...",
    "app_version": "1.7.20",
    "version_code": 71,
    "running": true,
    "port": 8790,
    "pool_summary": {},
    "manufacturer": "Xiaomi",
    "model": "23127PN0CC",
    "android_version": "15",
    "first_seen_at": "2026-10-20 08:00:00",
    "last_seen_at": "2026-10-25 06:30:00",
    "state": "online",
    "alert": false,
    "provider_sync": {
      "providers": [
        {
          "provider_id": "trae",
          "enabled": true,
          "accounts_total": 2,
          "accounts_ok": 2,
          "last_error": "",
          "last_call_at": 1730000000
        }
      ],
      "received_at": "2026-10-25 06:29:00"
    },
    "switches": [
      {
        "provider_id": "trae",
        "enabled": false,
        "reason": "上游风控",
        "updated_at": "2026-10-24 21:00:00"
      }
    ]
  }
}
```

- `provider_sync` 为 `null` 表示该设备从未上报供应商状态
- `switches` 只含**被后台显式设置过**的项；未出现表示沿用设备本地状态

失败：`404 device not found`

---

### 3.3 远程开关 — `POST /panel/api/switch`

按「设备 × 供应商」控制是否启用。

```json
{
  "device_id": "0123...",
  "provider_id": "trae",
  "enabled": false,
  "reason": "上游风控"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `device_id` | 是 | 目标设备 |
| `provider_id` | 是 | 如 `trae` / `loomy` / `custom:openrouter` |
| `enabled` | 是 | `true` 启用，`false` 停用 |
| `reason` | 否 | 停用原因，≤200 字，会下发给 APP 展示 |

成功：`{"code":0}`

> 生效时机：设备下次心跳时通过 `provider_overrides` 下发。
> 语义是**可逆软禁用**——只把供应商 ID 加入 `disabledProviders`，不影响账号凭证，用户可在 APP 内手动恢复。

---

## 4. 公告

### 4.1 列表 — `GET /panel/api/announcements`

返回最近 200 条（含已停用）。

```json
{
  "code": 0,
  "data": {
    "announcements": [
      {
        "id": 1,
        "dedup_key": "2026-11-maintenance",
        "title": "维护通知",
        "body": "今晚 23:00 起进行上游维护。",
        "body_hash": "9f2c...(64位hex)",
        "level": "warn",
        "action_label": "查看详情",
        "action_url": "https://example.com/notice",
        "min_code": null,
        "max_code": null,
        "enabled": 1,
        "starts_at": null,
        "ends_at": null,
        "created_at": "2026-10-24 20:00:00"
      }
    ]
  }
}
```

---

### 4.2 新增/编辑 — `POST /panel/api/announcement/save`

`id` 为 0 或不传表示新增，大于 0 表示编辑。

```json
{
  "id": 0,
  "dedup_key": "2026-11-maintenance",
  "title": "维护通知",
  "body": "今晚 23:00 起进行上游维护。",
  "level": "warn",
  "action_label": "查看详情",
  "action_url": "https://example.com/notice",
  "min_code": "",
  "max_code": "",
  "starts_at": "2026-11-01 00:00:00",
  "ends_at": "2026-11-02 00:00:00",
  "enabled": true
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `dedup_key` | 是 | ≤100 字，**全局唯一**；APP 用它 + `body_hash` 去重 |
| `title` | 是 | ≤200 字 |
| `body` | 是 | 正文，服务端自动算 `body_hash`(sha256) |
| `level` | 否 | `info` / `warn` / `critical`，默认 `info` |
| `action_label` / `action_url` | 否 | 按钮文案与链接 |
| `min_code` / `max_code` | 否 | 版本区间过滤，空表示不限 |
| `starts_at` / `ends_at` | 否 | 时间窗，格式 `YYYY-MM-DD HH:MM:SS`，空表示不限 |
| `enabled` | 否 | 默认 false（注意：需显式传 true 才下发） |

成功：

```json
{ "code": 0, "data": { "id": 1 } }
```

失败：`400 dedup_key/title/body required`

> **去重机制**：APP 用 `dedup_key + body_hash` 判断是否已展示。
> 同 `dedup_key` 但正文改了 → `body_hash` 变化 → 会重新弹一次。

---

### 4.3 删除 — `POST /panel/api/announcement/delete`

```json
{ "id": 1 }
```

成功：`{"code":0}`

---

## 5. 版本

### 5.1 列表 — `GET /panel/api/releases`

返回最近 200 个版本，按 `version_code` 倒序。

```json
{
  "code": 0,
  "data": {
    "releases": [
      {
        "id": 1,
        "version": "1.7.21",
        "version_code": 72,
        "changelog": "- 修复 xxx",
        "apk_url": "https://gw-admin.example.com/apk/app.apk",
        "apk_size": 7681628,
        "apk_sha256": "a3f1...(64位hex)",
        "force_update": 0,
        "rollout_percent": 100,
        "published_at": "2026-10-25 06:00:00"
      }
    ]
  }
}
```

---

### 5.2 发布/更新 — `POST /panel/api/release/save`

以 `version` / `version_code` 唯一键 UPSERT。

```json
{
  "version": "1.7.21",
  "version_code": 72,
  "changelog": "- 修复 xxx\n- 优化 yyy",
  "apk_url": "https://gw-admin.example.com/apk/app.apk",
  "apk_size": 7681628,
  "apk_sha256": "a3f1...(64位hex)",
  "force_update": false,
  "rollout_percent": 100
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `version` | 是 | 版本名，≤20 字 |
| `version_code` | 是 | 必须 > 0，用于与 APP 比对 |
| `apk_url` | 是 | ≤500 字 |
| `changelog` | 否 | 更新说明 |
| `apk_size` | 否 | 字节数，APP 下载后校验 |
| `apk_sha256` | 否 | **必须是 64 位 hex**，否则 400 |
| `force_update` | 否 | 强制更新标记 |
| `rollout_percent` | 否 | 灰度百分比 0-100，默认 100 |

成功：`{"code":0}`

失败：

| code | 场景 |
|---|---|
| 400 | 缺 version/version_code/apk_url，或 version_code ≤ 0 |
| 400 | `apk_sha256` 不是 64 位 hex |

> **灰度机制**：`crc32(device_id) % 100 < rollout_percent` 才下发，同一设备结果稳定。
> **强制更新两级**：本版本 `force_update=1`，或 APP 的 `version_code` 低于全局 `min_supported_code`。

---

### 5.3 删除 — `POST /panel/api/release/delete`

```json
{ "id": 1 }
```

成功：`{"code":0}`

---

## 6. 反馈

### 6.1 列表 — `GET /panel/api/bugs?status=open`

`status` 可选：`open` / `fixing` / `done`，不传返回全部。最近 200 条。

```json
{
  "code": 0,
  "data": {
    "bugs": [
      {
        "id": 12,
        "device_id": "0123...",
        "app_version": "1.7.20",
        "title": "Loomy 登录后倒计时不重置",
        "detail": "登录成功后验证码输入框未清空。",
        "log_excerpt": "2026-10-24 21:15:19 [INFO] 登录…",
        "severity": "mid",
        "status": "open",
        "created_at": "2026-10-24 21:20:00"
      }
    ]
  }
}
```

---

### 6.2 更新状态 — `POST /panel/api/bug/status`

```json
{ "id": 12, "status": "fixing" }
```

`status` 必须是 `open` / `fixing` / `done` 之一。

成功：`{"code":0}`
失败：`400 id/status required`

---

## 7. 全局配置

### 7.1 读取 — `GET /panel/api/settings`

```json
{
  "code": 0,
  "data": {
    "settings": {
      "min_supported_code": "0",
      "heartbeat_interval_sec": "60",
      "alert_webhook": ""
    }
  }
}
```

---

### 7.2 保存 — `POST /panel/api/settings`

**白名单机制**：只接受下列三项，其它键被忽略。

```json
{
  "min_supported_code": "0",
  "heartbeat_interval_sec": "60",
  "alert_webhook": "https://qyapi.weixin.qq.com/..."
}
```

| 键 | 说明 |
|---|---|
| `min_supported_code` | 全局最低支持 versionCode，低于则**强制更新**；`0` 表示关闭 |
| `heartbeat_interval_sec` | 心跳间隔，服务端会夹到 30~3600 |
| `alert_webhook` | 异常告警 Webhook，留空不通知 |

成功：`{"code":0}`

---

## 8. 错误处理约定

### HTTP 状态与业务码

| HTTP | code | 含义 | 前端处理 |
|---|---|---|---|
| 200 | 0 | 成功 | 正常处理 `data` |
| 400 | 400 | 参数错误 | 提示 `msg` |
| 401 | 401 | 未登录 / 会话超时 | 跳回登录页 |
| 403 | 403 | 已完成初始化 | 跳回登录页 |
| 404 | 404 | 资源不存在 | 提示 `msg` |
| 429 | 429 | 触发限流 | 提示稍后再试 |
| 500 | 500 | 服务端异常 | 提示稍后再试 |

### 前端推荐封装

```js
async function api(path, opts = {}) {
  const res = await fetch('/panel/api/' + path, {
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    ...opts,
  });
  if (res.status === 401) { showLogin(); throw new Error('未登录'); }
  const data = await res.json().catch(() => ({}));
  if (data.code !== 0) throw new Error(data.msg || '请求失败');
  return data.data;
}
```

### 安全说明

- 所有写接口都在 `Auth::requireAdmin()` 之后，未登录无法调用
- `switch` 会记录操作者 `updated_by`（`admins.id`）
- 登录失败、登录成功、初始化、限流、签名失败均写入 `audit_logs`
