# AI 网关 PHP 后台

配合 Android AI 网关使用的轻量后台：设备心跳、远程公告、版本发布、供应商远程开关、Bug 反馈。
原生 PHP 8.0+，仅依赖 PDO MySQL，无框架、无 Composer。

本目录可整体拷贝走独立部署。

---

## 目录结构

```
aigw-admin/
├── public/                 # Web 根目录（nginx root 指向这里）
│   ├── index.php           # 前端控制器（APP API + 面板 API 路由）
│   └── panel/index.html    # 管理面板单页（原生 JS，无外部依赖）
├── src/
│   ├── bootstrap.php       # 配置加载、PDO、通用函数
│   ├── Auth.php            # HMAC 验签 + 管理员 Session 登录
│   ├── Guard.php           # 敏感字段过滤（D2）+ 文件限流
│   ├── Support/Audit.php   # 审计日志
│   └── Controller/
│       ├── Device.php          # A2 设备注册
│       ├── Heartbeat.php       # E1 合并下发（开关/公告/版本/配置）
│       ├── Announcement.php    # B1 列表 + B2 已读上报
│       ├── Release.php         # C1 版本检查
│       ├── ProviderSync.php    # D1 供应商状态上报
│       ├── Bug.php             # F1 反馈提交
│       └── Panel.php           # 管理面板 API（含 D4 矩阵、E2 三态）
├── cron/
│   ├── cleanup.php         # G2 数据保留期清理（每日）
│   └── alert.php           # E3 异常告警（每 10 分钟）
├── bin/create_admin.php    # 创建管理员
├── schema.sql              # 数据库结构
├── config.php              # 配置（密钥从环境变量读）
├── API.md                  # APP 端接口文档（HMAC 签名）
├── PANEL-API.md            # 管理面板接口文档（Session）
└── var/                    # 运行期可写目录（限流计数）
```

## 文档

| 文档 | 面向 | 内容 |
|---|---|---|
| [API.md](API.md) | Android APP 开发者 | `/api/v1/*` 接口、HMAC 签名、心跳响应处理 |
| [PANEL-API.md](PANEL-API.md) | 前端/二次开发者 | `/panel/api/*` 接口、登录与初始化、各模块字段 |

---

## 部署

### 1. 数据库

```sh
mysql -u root -p < schema.sql
```

### 2. 环境变量

**必须**设置 `AIGW_HMAC_SECRET`（≥32 字节随机串），其它可选：

| 变量 | 说明 | 默认 |
|---|---|---|
| `AIGW_HMAC_SECRET` | 设备请求签名密钥（**必填**） | — |
| `DB_HOST` / `DB_PORT` | MySQL 地址 | `127.0.0.1:3306` |
| `DB_NAME` | 库名 | `aigw_admin` |
| `DB_USER` / `DB_PASS` | 账号密码 | `aigw` / 空 |

生成密钥：`openssl rand -hex 32`

### 3. 创建管理员

**推荐：打开面板 `/panel/` 直接创建**（首次进入会显示创建管理员页面）。

或用命令行：

```sh
AIGW_HMAC_SECRET=... DB_PASS=... php bin/create_admin.php admin 你的密码
```

### 4. nginx 配置示例

```nginx
server {
    listen 443 ssl;
    server_name gw-admin.example.com;
    root /path/to/aigw-admin/public;
    index index.php;

    # PHP 只处理动态请求
    location / {
        try_files $uri $uri/ /index.php?$query_string;
    }
    location ~ \.php$ {
        include fastcgi_params;
        fastcgi_pass unix:/run/php/php8.2-fpm.sock;
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
        fastcgi_param AIGW_HMAC_SECRET "你的密钥";   # 或写进 fpm pool env
        fastcgi_param DB_PASS "你的密码";
    }

    # API 基础限流（PHP 层还有兜底）
    limit_req_zone $binary_remote_addr zone=aigw_api:10m rate=20r/s;
    location /api/ { limit_req zone=aigw_api burst=40 nodelay; try_files $uri /index.php?$query_string; }

    # APK 交给静态服务，PHP 只返回 URL
    location /apk/ { alias /path/to/apk/; }
}
```

`var/` 需对 php-fpm 运行用户可写。

### 5. 定时任务

```cron
0 3 * * *  php /path/to/aigw-admin/cron/cleanup.php >> /var/log/aigw-cleanup.log 2>&1
*/10 * * * * php /path/to/aigw-admin/cron/alert.php   >> /var/log/aigw-alert.log 2>&1
```

---

## 面板

访问 `https://gw-admin.example.com/panel/`。

### 首次使用

打开面板会看到「首次使用 · 创建管理员」页面，直接填写用户名（3-50 位字母数字下划线）与密码（至少 10 位）即可。

初始化入口的安全约束：
- 仅当 `admins` 表**为空**时可用，创建成功后自动关闭
- 已初始化后再调用返回 `403 already initialized`
- 同 IP 5 分钟内最多 5 次尝试

> 若担心被人抢先初始化，部署完立刻打开面板建号，或用命令行：
> `php bin/create_admin.php admin 你的密码`

### 登录安全

- 密码 `password_hash` / `password_verify`（bcrypt）
- 会话 Cookie：`HttpOnly` + `Secure`（HTTPS 下）+ `SameSite=Lax`
- 登录成功 `session_regenerate_id(true)` 防会话固定
- **空闲 2 小时自动失效**（`config.php` 的 `admin_session_idle`，0 表示不超时）
- 登录失败与成功都写审计日志（`admin_login_failed` / `admin_login_ok`）
- 同 IP 登录尝试：60 秒内最多 10 次

### 功能

概览统计（在线/沉默/离线/未处理反馈）、设备列表与详情（供应商矩阵 + 远程开关）、公告增删改查、版本发布、反馈流转、全局配置。

---

## APP 端对接

详细接口约定（签名算法、请求/响应字段、心跳响应处理）见 **[API.md](API.md)**。

### 签名

每个请求带三个头：

```
X-Device-Id: <32 位设备 ID>
X-Signature: <hex(hmac_sha256(rawBody, AIGW_HMAC_SECRET))>
X-Timestamp: <unix 秒>          # 可选，服务端容差 300s
```

`X-Signature` 对**原始请求体字节**做 HMAC-SHA256；GET 请求对空串签名。

```kotlin
val raw = body.toString()
val sig = hmacSha256(raw.toByteArray(), secret).hex()
```

### 接口

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/register` | 首次启动注册（body 带 `app_version`/`version_code`/`device{manufacturer,model,android_version}`） |
| POST | `/api/v1/heartbeat` | 心跳，返回合并下发（见下） |
| GET | `/api/v1/announcements?version_code=N` | 单独拉取公告 |
| POST | `/api/v1/announcements/read` | 已读上报 `{dedup_key}` |
| GET | `/api/v1/release/check?version_code=N` | 版本检查 |
| POST | `/api/v1/sync/providers` | 供应商状态上报 `{providers:[...]}` |
| POST | `/api/v1/bugs` | 反馈 `{title,detail,log_excerpt,severity,app_version}` |

### 心跳响应

```json
{
  "code": 0,
  "data": {
    "interval_sec": 60,
    "server_time": 1700000000,
    "provider_overrides": [
      { "provider_id": "trae", "enabled": false, "reason": "上游风控" }
    ],
    "announcements": [
      { "dedup_key": "2024-11-maintenance", "title": "维护通知", "body": "...",
        "body_hash": "<sha256>", "level": "warn", "action_label": null, "action_url": null }
    ],
    "release": {
      "version": "1.7.21", "version_code": 72, "changelog": "...",
      "apk_url": "https://...", "apk_size": 7681628, "apk_sha256": "<64 hex>",
      "force_update": false
    },
    "config": { "heartbeat_interval_sec": 60 }
  }
}
```

APP 侧处理建议：

- `provider_overrides` → **可逆软禁用**（与凭证失效 `SESSION_DEAD` 的硬禁用区分，远端恢复时能自动启用）
- `announcements` → 以 `dedup_key + body_hash` 判重；`critical` 弹窗、`warn` 横幅、`info` 列表
- `release` → 走现有更新流程；`force_update` 为 true 时阻塞弹窗；下载后校验 `apk_size` 与 `apk_sha256`
- 上报失败（网络/5xx）→ 静默重试 + 本地队列，**绝不能影响网关主流程**

### 供应商上报格式

```json
{
  "providers": [
    { "provider_id": "trae", "enabled": true, "accounts_total": 2, "accounts_ok": 2,
      "last_error": "", "last_call_at": 1700000000 }
  ]
}
```

**严禁**携带任何凭证字段。服务端 `Guard` 会递归扫描，命中 `token`/`session`/`cookie`/`uid`/`phone` 等字段直接整批拒绝并写审计日志（`leak_attempt`）。

---

## 上线检查清单

- [ ] `AIGW_HMAC_SECRET` ≥ 32 字节随机串，仅存环境变量，不入库不入 git
- [ ] nginx 对 `/api/` 限流；PHP 层文件限流兜底
- [ ] `var/` 目录可写，且不在 Web 可访问路径内
- [ ] 面板登录使用 `password_hash`/`password_verify`，Cookie 已设 `HttpOnly` + `Secure` + `SameSite=Lax`
- [ ] APK 走 nginx 静态服务，PHP 只返回 URL
- [ ] crontab 两项已挂，`alert_webhook` 已配置
- [ ] 全链路 HTTPS
- [ ] `provider_syncs` 定期抽查，确认无凭证字段
