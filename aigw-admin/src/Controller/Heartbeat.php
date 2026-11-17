<?php
/**
 * E1 心跳：单次请求合并下发
 * - 供应商远程开关（D3）
 * - 公告（B1/B4，按版本与时间窗过滤）
 * - 版本更新（C1/C2/C3）
 * - 远程可调参数（E4）
 */
declare(strict_types=1);

final class HeartbeatController
{
    public function handle(array $ctx): void
    {
        $deviceId = $ctx['device_id'];
        $body = $ctx['body'];
        $now = date('Y-m-d H:i:s');
        $appCode = (int)($body['version_code'] ?? 0);

        // 1) UPSERT 设备（单条 SQL，避免读-改-写）
        db()->prepare(
            'INSERT INTO devices (device_id, app_version, version_code, running, port, pool_summary, first_seen_at, last_seen_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE app_version = VALUES(app_version), version_code = VALUES(version_code),
               running = VALUES(running), port = VALUES(port), pool_summary = VALUES(pool_summary),
               last_seen_at = VALUES(last_seen_at)'
        )->execute([
            $deviceId,
            mb_substr((string)($body['app_version'] ?? ''), 0, 20),
            $appCode,
            !empty($body['running']) ? 1 : 0,
            (int)($body['port'] ?? 0),
            json_encode($this->normalizePoolSummary($body['pool_summary'] ?? []), JSON_UNESCAPED_UNICODE),
            $now,
            $now,
        ]);

        // 2) 远程可调参数
        $interval = max(30, min(3600, (int)(setting('heartbeat_interval_sec', '60') ?? 60)));

        json_out(['code' => 0, 'data' => [
            'interval_sec'       => $interval,
            'server_time'        => time(),
            'provider_overrides' => $this->providerOverrides($deviceId),
            'announcements'      => $this->announcements($appCode),
            'release'            => $this->latestRelease($deviceId, $appCode),
            'config'             => ['heartbeat_interval_sec' => $interval],
        ]]);
    }

    /** 只保留白名单字段，避免 APP 上报结构变化污染库。 */
    private function normalizePoolSummary(mixed $raw): array
    {
        if (!is_array($raw)) {
            return [];
        }
        $out = [];
        foreach ($raw as $providerId => $stat) {
            if (!is_string($providerId) || !is_array($stat)) {
                continue;
            }
            $key = substr(preg_replace('/[^0-9a-zA-Z:_\-]/', '', $providerId) ?? '', 0, 60);
            if ($key === '') {
                continue;
            }
            $total = max(0, min(999, (int)($stat['total'] ?? 0)));
            $out[$key] = [
                'total' => $total,
                'ok'    => max(0, min($total, (int)($stat['ok'] ?? 0))),
            ];
        }
        return $out;
    }

    /** D3：设备 × 供应商开关，只下发与默认值不同的项。 */
    private function providerOverrides(string $deviceId): array
    {
        $st = db()->prepare('SELECT provider_id, enabled, reason FROM provider_switches WHERE device_id = ?');
        $st->execute([$deviceId]);
        $rows = $st->fetchAll();
        return array_map(static fn(array $r) => [
            'provider_id' => (string)$r['provider_id'],
            'enabled'     => (bool)$r['enabled'],
            'reason'      => $r['reason'] !== null ? (string)$r['reason'] : null,
        ], $rows);
    }

    /** B1/B4：版本过滤 + 时间窗，critical 优先。 */
    private function announcements(int $appCode): array
    {
        $sql = 'SELECT dedup_key, title, body, body_hash, level, action_label, action_url
                FROM announcements
                WHERE enabled = 1
                  AND (starts_at IS NULL OR starts_at <= NOW())
                  AND (ends_at   IS NULL OR ends_at   >= NOW())
                  AND (min_code  IS NULL OR ? >= min_code)
                  AND (max_code  IS NULL OR ? <= max_code)
                ORDER BY FIELD(level, \'critical\', \'warn\', \'info\'), created_at DESC
                LIMIT 3';
        $st = db()->prepare($sql);
        $st->execute([$appCode, $appCode]);
        return array_map(static fn(array $r) => [
            'dedup_key'    => (string)$r['dedup_key'],
            'title'        => (string)$r['title'],
            'body'         => (string)$r['body'],
            'body_hash'    => (string)$r['body_hash'],
            'level'        => (string)$r['level'],
            'action_label' => $r['action_label'] !== null ? (string)$r['action_label'] : null,
            'action_url'   => $r['action_url'] !== null ? (string)$r['action_url'] : null,
        ], $st->fetchAll());
    }

    /** C1/C2/C3：最新版本 + 强制更新 + 灰度放量。 */
    private function latestRelease(string $deviceId, int $appCode): ?array
    {
        $row = db()->query(
            'SELECT version, version_code, changelog, apk_url, apk_size, apk_sha256, force_update, rollout_percent
             FROM releases ORDER BY version_code DESC LIMIT 1'
        )->fetch();
        if (!$row || (int)$row['version_code'] <= $appCode) {
            return null;
        }

        // C3 灰度：按 device_id 稳定哈希落桶，保证同一设备结果一致
        $rollout = max(0, min(100, (int)$row['rollout_percent']));
        if ($rollout < 100) {
            $bucket = crc32($deviceId) % 100;
            if ($bucket >= $rollout) {
                return null;
            }
        }

        // C2 两级强制：单版本 force 或低于全局最低支持线
        $minSupported = (int)(setting('min_supported_code', '0') ?? 0);
        $force = (bool)$row['force_update'] || ($minSupported > 0 && $appCode < $minSupported);

        return [
            'version'      => (string)$row['version'],
            'version_code' => (int)$row['version_code'],
            'changelog'    => (string)$row['changelog'],
            'apk_url'      => (string)$row['apk_url'],
            'apk_size'     => $row['apk_size'] !== null ? (int)$row['apk_size'] : null,
            'apk_sha256'   => $row['apk_sha256'] !== null ? (string)$row['apk_sha256'] : null,
            'force_update' => $force,
        ];
    }
}
