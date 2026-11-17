<?php
/**
 * Web 管理面板 API（Session 鉴权）。
 *
 * 路由前缀：/panel/api/<action>
 * 返回统一 {"code":0,"data":...} / {"code":4xx,"msg":"..."}。
 */
declare(strict_types=1);

require_once __DIR__ . '/../Auth.php';
require_once __DIR__ . '/../Guard.php';

final class Panel
{
    /** 三态在线判定阈值（分钟）。 */
    private const ONLINE_MINUTES = 5;
    private const SILENT_MINUTES = 60 * 6;

    public static function dispatch(string $path, string $method): void
    {
        $action = substr($path, strlen('panel/api/'));
        $action = trim($action, '/');
        $method = strtoupper($method);

        // 登录/登出/初始化不需要已登录
        if ($action === 'login' && $method === 'POST') {
            self::login();
        }
        if ($action === 'logout' && $method === 'POST') {
            Auth::logout();
            json_out(['code' => 0]);
        }
        // 首次初始化：admins 表为空时才能用
        if ($action === 'init' && $method === 'POST') {
            self::init();
        }
        if ($action === 'init-status' && $method === 'GET') {
            json_out(['code' => 0, 'data' => ['needs_init' => !Auth::hasAnyAdmin()]]);
        }

        Auth::requireAdmin();

        if ($action === 'whoami' && $method === 'GET') {
            json_out(['code' => 0, 'data' => ['admin_id' => Auth::adminId()]]);
        }

        switch ("$method $action") {
            case 'GET stats':
                self::stats();
            case 'GET devices':
                self::devices();
            case 'GET device':
                self::device();
            case 'POST switch':
                self::switchProvider();
            case 'GET announcements':
                self::announcements();
            case 'POST announcement/save':
                self::saveAnnouncement();
            case 'POST announcement/delete':
                self::deleteAnnouncement();
            case 'GET releases':
                self::releases();
            case 'POST release/save':
                self::saveRelease();
            case 'POST release/delete':
                self::deleteRelease();
            case 'GET settings':
                self::settings();
            case 'POST settings':
                self::saveSettings();
            case 'GET bugs':
                self::bugs();
            case 'POST bug/status':
                self::updateBugStatus();
            default:
                json_out(['code' => 404, 'msg' => 'unknown action'], 404);
        }

        json_out(['code' => 500, 'msg' => 'unreachable'], 500);
    }

    // ------------------------------------------------------------------ 登录

    /**
     * 首次初始化管理员。仅当 admins 表为空时可用——否则任何知道地址的人都能抢先建号。
     */
    private static function init(): void
    {
        if (Auth::hasAnyAdmin()) {
            json_out(['code' => 403, 'msg' => 'already initialized'], 403);
        }
        if (!Guard::rateLimit('admin_init_' . ($_SERVER['REMOTE_ADDR'] ?? ''), 300, 5)) {
            json_out(['code' => 429, 'msg' => 'too many attempts'], 429);
        }
        $body = request_body();
        $username = is_scalar($body['username'] ?? null) ? trim((string)$body['username']) : '';
        $password = is_scalar($body['password'] ?? null) ? (string)$body['password'] : '';
        if (!preg_match('/^[a-zA-Z0-9_]{3,50}$/', $username)) {
            json_out(['code' => 400, 'msg' => '用户名需 3-50 位字母数字下划线'], 400);
        }
        if (strlen($password) < 10) {
            json_out(['code' => 400, 'msg' => '密码至少 10 位'], 400);
        }
        try {
            Auth::bootstrapFirstAdmin($username, $password);
        } catch (RuntimeException $e) {
            json_out(['code' => 403, 'msg' => 'already initialized'], 403);
        }
        Auth::attemptLogin($username, $password);
        json_out(['code' => 0, 'data' => ['admin' => $username]]);
    }

    private static function login(): void
    {
        if (!Guard::rateLimit('admin_login_' . ($_SERVER['REMOTE_ADDR'] ?? ''), 60, 10)) {
            json_out(['code' => 429, 'msg' => 'too many attempts'], 429);
        }
        $body = request_body();
        $username = is_scalar($body['username'] ?? null) ? trim((string)$body['username']) : '';
        $password = is_scalar($body['password'] ?? null) ? (string)$body['password'] : '';
        if ($username === '' || $password === '') {
            json_out(['code' => 400, 'msg' => 'username/password required'], 400);
        }
        if (!Auth::attemptLogin($username, $password)) {
            json_out(['code' => 401, 'msg' => 'invalid credentials'], 401);
        }
        json_out(['code' => 0, 'data' => ['admin' => $username]]);
    }

    // ------------------------------------------------------------------ 概览

    private static function stats(): void
    {
        $now = time();
        $onlineAt = date('Y-m-d H:i:s', $now - self::ONLINE_MINUTES * 60);
        $silentAt = date('Y-m-d H:i:s', $now - self::SILENT_MINUTES * 60);

        $total = (int)db()->query('SELECT COUNT(*) FROM devices')->fetchColumn();

        $stmt = db()->prepare('SELECT COUNT(*) FROM devices WHERE last_seen_at >= ?');
        $stmt->execute([$onlineAt]);
        $online = (int)$stmt->fetchColumn();

        // 沉默：网关自报运行中，但心跳已超过阈值
        $stmt = db()->prepare('SELECT COUNT(*) FROM devices WHERE running = 1 AND last_seen_at < ?');
        $stmt->execute([$silentAt]);
        $silent = (int)$stmt->fetchColumn();

        // 离线：既非在线也非沉默（含网关已停止与长时间无心跳）
        $offline = max(0, $total - $online - $silent);

        $openBugs = (int)db()->query("SELECT COUNT(*) FROM bug_reports WHERE status <> 'done'")->fetchColumn();

        json_out(['code' => 0, 'data' => [
            'devices_total'  => $total,
            'devices_online' => $online,
            'devices_silent' => $silent,
            'devices_offline'=> $offline,
            'bugs_open'      => $openBugs,
        ]]);
    }

    // ------------------------------------------------------------------ 设备

    private static function devices(): void
    {
        $rows = db()->query(
            'SELECT d.device_id, d.app_version, d.version_code, d.running, d.port, d.pool_summary,
                    d.first_seen_at, d.last_seen_at, m.manufacturer, m.model, m.android_version
             FROM devices d LEFT JOIN device_meta m ON m.device_id = d.device_id
             ORDER BY d.last_seen_at DESC LIMIT 500'
        )->fetchAll();

        json_out(['code' => 0, 'data' => ['devices' => array_map([self::class, 'decorateDevice'], $rows)]]);
    }

    private static function device(): void
    {
        $deviceId = (string)($_GET['device_id'] ?? '');
        if ($deviceId === '') {
            json_out(['code' => 400, 'msg' => 'device_id required'], 400);
        }
        $st = db()->prepare(
            'SELECT d.*, m.manufacturer, m.model, m.android_version
             FROM devices d LEFT JOIN device_meta m ON m.device_id = d.device_id
             WHERE d.device_id = ?'
        );
        $st->execute([$deviceId]);
        $row = $st->fetch();
        if (!$row) {
            json_out(['code' => 404, 'msg' => 'device not found'], 404);
        }

        $st = db()->prepare('SELECT sync_json, received_at FROM provider_syncs WHERE device_id = ?');
        $st->execute([$deviceId]);
        $sync = $st->fetch();
        $syncedProviders = [];
        if ($sync) {
            $decoded = json_decode((string)$sync['sync_json'], true);
            if (is_array($decoded) && isset($decoded['providers']) && is_array($decoded['providers'])) {
                $syncedProviders = $decoded['providers'];
            }
        }

        $st = db()->prepare('SELECT provider_id, enabled, reason, updated_at FROM provider_switches WHERE device_id = ?');
        $st->execute([$deviceId]);
        $switches = $st->fetchAll();

        $data = self::decorateDevice($row);
        $data['provider_sync'] = $sync ? [
            'providers'   => $syncedProviders,
            'received_at' => $sync['received_at'],
        ] : null;
        $data['switches'] = array_map(static fn(array $r) => [
            'provider_id' => (string)$r['provider_id'],
            'enabled'     => (bool)$r['enabled'],
            'reason'      => $r['reason'],
            'updated_at'  => $r['updated_at'],
        ], $switches);

        json_out(['code' => 0, 'data' => $data]);
    }

    /** 计算三态并附加异常标记（E2/E3）。 */
    private static function decorateDevice(array $row): array
    {
        $lastSeen = $row['last_seen_at'] !== null ? strtotime((string)$row['last_seen_at']) : 0;
        $ageMin = $lastSeen > 0 ? (time() - $lastSeen) / 60 : PHP_INT_MAX;
        $state = match (true) {
            $lastSeen <= 0 => 'offline',
            $ageMin <= self::ONLINE_MINUTES => 'online',
            $ageMin <= self::SILENT_MINUTES => 'silent',
            default => 'offline',
        };
        $running = (bool)$row['running'];
        $alert = $running && $state !== 'online';

        return [
            'device_id'      => (string)$row['device_id'],
            'app_version'    => (string)($row['app_version'] ?? ''),
            'version_code'   => (int)($row['version_code'] ?? 0),
            'running'        => $running,
            'port'           => (int)($row['port'] ?? 0),
            'pool_summary'   => json_decode((string)($row['pool_summary'] ?? '{}'), true) ?: new stdClass(),
            'manufacturer'   => (string)($row['manufacturer'] ?? ''),
            'model'          => (string)($row['model'] ?? ''),
            'android_version'=> (string)($row['android_version'] ?? ''),
            'first_seen_at'  => $row['first_seen_at'],
            'last_seen_at'   => $row['last_seen_at'],
            'state'          => $state,
            'alert'          => $alert,
        ];
    }

    /** D3：远程启停某设备的某供应商。 */
    private static function switchProvider(): void
    {
        $adminId = Auth::requireAdmin();
        $body = request_body();
        $deviceId = is_scalar($body['device_id'] ?? null) ? (string)$body['device_id'] : '';
        $providerId = is_scalar($body['provider_id'] ?? null) ? (string)$body['provider_id'] : '';
        if ($deviceId === '' || $providerId === '') {
            json_out(['code' => 400, 'msg' => 'device_id/provider_id required'], 400);
        }
        $enabled = !empty($body['enabled']);
        $reason = is_scalar($body['reason'] ?? null) ? mb_substr(trim((string)$body['reason']), 0, 200) : null;

        db()->prepare(
            'INSERT INTO provider_switches (device_id, provider_id, enabled, reason, updated_by)
             VALUES (?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), reason = VALUES(reason), updated_by = VALUES(updated_by)'
        )->execute([$deviceId, $providerId, $enabled ? 1 : 0, $reason, $adminId]);

        json_out(['code' => 0]);
    }

    // ------------------------------------------------------------------ 公告

    private static function announcements(): void
    {
        $rows = db()->query(
            'SELECT id, dedup_key, title, body, body_hash, level, action_label, action_url,
                    min_code, max_code, enabled, starts_at, ends_at, created_at
             FROM announcements ORDER BY created_at DESC LIMIT 200'
        )->fetchAll();
        json_out(['code' => 0, 'data' => ['announcements' => $rows]]);
    }

    private static function saveAnnouncement(): void
    {
        $body = request_body();
        $id = (int)($body['id'] ?? 0);
        $dedupKey = self::text($body['dedup_key'] ?? '', 100);
        $title = self::text($body['title'] ?? '', 200);
        $text = self::text($body['body'] ?? '', 20000);
        if ($dedupKey === '' || $title === '' || $text === '') {
            json_out(['code' => 400, 'msg' => 'dedup_key/title/body required'], 400);
        }
        $level = in_array(($body['level'] ?? ''), ['info', 'warn', 'critical'], true) ? $body['level'] : 'info';
        $bodyHash = hash('sha256', $text);
        $actionLabel = self::nullable($body['action_label'] ?? null, 50);
        $actionUrl = self::nullable($body['action_url'] ?? null, 500);
        $minCode = isset($body['min_code']) && $body['min_code'] !== '' ? (int)$body['min_code'] : null;
        $maxCode = isset($body['max_code']) && $body['max_code'] !== '' ? (int)$body['max_code'] : null;
        $enabled = !empty($body['enabled']) ? 1 : 0;
        $startsAt = self::nullable($body['starts_at'] ?? null, 19);
        $endsAt = self::nullable($body['ends_at'] ?? null, 19);

        if ($id > 0) {
            db()->prepare(
                'UPDATE announcements SET dedup_key=?, title=?, body=?, body_hash=?, level=?,
                        action_label=?, action_url=?, min_code=?, max_code=?, enabled=?, starts_at=?, ends_at=?
                 WHERE id=?'
            )->execute([$dedupKey, $title, $text, $bodyHash, $level, $actionLabel, $actionUrl,
                        $minCode, $maxCode, $enabled, $startsAt, $endsAt, $id]);
        } else {
            db()->prepare(
                'INSERT INTO announcements (dedup_key, title, body, body_hash, level,
                        action_label, action_url, min_code, max_code, enabled, starts_at, ends_at)
                 VALUES (?,?,?,?,?,?,?,?,?,?,?,?)'
            )->execute([$dedupKey, $title, $text, $bodyHash, $level, $actionLabel, $actionUrl,
                        $minCode, $maxCode, $enabled, $startsAt, $endsAt]);
        }
        json_out(['code' => 0, 'data' => ['id' => $id > 0 ? $id : (int)db()->lastInsertId()]]);
    }

    private static function deleteAnnouncement(): void
    {
        $id = (int)(request_body()['id'] ?? 0);
        if ($id <= 0) {
            json_out(['code' => 400, 'msg' => 'id required'], 400);
        }
        db()->prepare('DELETE FROM announcements WHERE id = ?')->execute([$id]);
        json_out(['code' => 0]);
    }

    // ------------------------------------------------------------------ 版本

    private static function releases(): void
    {
        $rows = db()->query(
            'SELECT id, version, version_code, changelog, apk_url, apk_size, apk_sha256,
                    force_update, rollout_percent, published_at
             FROM releases ORDER BY version_code DESC LIMIT 200'
        )->fetchAll();
        json_out(['code' => 0, 'data' => ['releases' => $rows]]);
    }

    private static function saveRelease(): void
    {
        $body = request_body();
        $version = self::text($body['version'] ?? '', 20);
        $versionCode = (int)($body['version_code'] ?? 0);
        $changelog = self::text($body['changelog'] ?? '', 20000);
        $apkUrl = self::text($body['apk_url'] ?? '', 500);
        if ($version === '' || $versionCode <= 0 || $apkUrl === '') {
            json_out(['code' => 400, 'msg' => 'version/version_code/apk_url required'], 400);
        }
        $apkSize = isset($body['apk_size']) && $body['apk_size'] !== '' ? (int)$body['apk_size'] : null;
        $sha = self::nullable($body['apk_sha256'] ?? null, 64);
        if ($sha !== null && !preg_match('/^[0-9a-f]{64}$/i', $sha)) {
            json_out(['code' => 400, 'msg' => 'apk_sha256 must be 64 hex chars'], 400);
        }
        $force = !empty($body['force_update']) ? 1 : 0;
        $rollout = max(0, min(100, (int)($body['rollout_percent'] ?? 100)));

        db()->prepare(
            'INSERT INTO releases (version, version_code, changelog, apk_url, apk_size, apk_sha256, force_update, rollout_percent)
             VALUES (?,?,?,?,?,?,?,?)
             ON DUPLICATE KEY UPDATE changelog=VALUES(changelog), apk_url=VALUES(apk_url),
               apk_size=VALUES(apk_size), apk_sha256=VALUES(apk_sha256),
               force_update=VALUES(force_update), rollout_percent=VALUES(rollout_percent)'
        )->execute([$version, $versionCode, $changelog, $apkUrl, $apkSize, $sha, $force, $rollout]);

        json_out(['code' => 0]);
    }

    private static function deleteRelease(): void
    {
        $id = (int)(request_body()['id'] ?? 0);
        if ($id <= 0) {
            json_out(['code' => 400, 'msg' => 'id required'], 400);
        }
        db()->prepare('DELETE FROM releases WHERE id = ?')->execute([$id]);
        json_out(['code' => 0]);
    }

    // ------------------------------------------------------------------ 配置

    private static function settings(): void
    {
        $rows = db()->query('SELECT k, v FROM settings')->fetchAll();
        $out = [];
        foreach ($rows as $r) {
            $out[(string)$r['k']] = (string)$r['v'];
        }
        json_out(['code' => 0, 'data' => ['settings' => $out]]);
    }

    private static function saveSettings(): void
    {
        $allowed = ['min_supported_code', 'heartbeat_interval_sec', 'alert_webhook'];
        $body = request_body();
        $input = is_array($body['settings'] ?? null) ? $body['settings'] : $body;
        foreach ($allowed as $key) {
            if (!array_key_exists($key, $input)) {
                continue;
            }
            setting_set($key, self::text($input[$key], 2000));
        }
        json_out(['code' => 0]);
    }

    // ------------------------------------------------------------------ Bug

    private static function bugs(): void
    {
        $status = (string)($_GET['status'] ?? '');
        $sql = 'SELECT b.id, b.device_id, b.app_version, b.title, b.detail, b.log_excerpt,
                       b.severity, b.status, b.created_at
                FROM bug_reports b';
        $params = [];
        if (in_array($status, ['open', 'fixing', 'done'], true)) {
            $sql .= ' WHERE b.status = ?';
            $params[] = $status;
        }
        $sql .= ' ORDER BY b.created_at DESC LIMIT 200';
        $st = db()->prepare($sql);
        $st->execute($params);
        json_out(['code' => 0, 'data' => ['bugs' => $st->fetchAll()]]);
    }

    private static function updateBugStatus(): void
    {
        $body = request_body();
        $id = (int)($body['id'] ?? 0);
        $status = (string)($body['status'] ?? '');
        if ($id <= 0 || !in_array($status, ['open', 'fixing', 'done'], true)) {
            json_out(['code' => 400, 'msg' => 'id/status required'], 400);
        }
        db()->prepare('UPDATE bug_reports SET status = ? WHERE id = ?')->execute([$status, $id]);
        json_out(['code' => 0]);
    }

    // ------------------------------------------------------------------ 工具

    private static function text(mixed $value, int $maxLen): string
    {
        if (!is_scalar($value)) {
            return '';
        }
        return trim(mb_substr((string)$value, 0, $maxLen));
    }

    private static function nullable(mixed $value, int $maxLen): ?string
    {
        $text = self::text($value, $maxLen);
        return $text === '' ? null : $text;
    }
}
