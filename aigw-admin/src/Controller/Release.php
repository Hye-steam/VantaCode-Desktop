<?php
/** C1 版本检查（APP 单独查询时，与心跳下发同一判定逻辑）。 */
declare(strict_types=1);

final class ReleaseController
{
    /** GET /api/v1/release/check?version_code=xxx */
    public function check(array $ctx): void
    {
        $deviceId = $ctx['device_id'];
        $appCode = (int)($_GET['version_code'] ?? 0);

        $row = db()->query(
            'SELECT version, version_code, changelog, apk_url, apk_size, apk_sha256, force_update, rollout_percent
             FROM releases ORDER BY version_code DESC LIMIT 1'
        )->fetch();

        if (!$row || (int)$row['version_code'] <= $appCode) {
            json_out(['code' => 0, 'data' => ['update' => null]]);
        }

        $rollout = max(0, min(100, (int)$row['rollout_percent']));
        if ($rollout < 100 && (crc32($deviceId) % 100) >= $rollout) {
            json_out(['code' => 0, 'data' => ['update' => null]]);
        }

        $minSupported = (int)(setting('min_supported_code', '0') ?? 0);
        $force = (bool)$row['force_update'] || ($minSupported > 0 && $appCode < $minSupported);

        json_out(['code' => 0, 'data' => ['update' => [
            'version'      => (string)$row['version'],
            'version_code' => (int)$row['version_code'],
            'changelog'    => (string)$row['changelog'],
            'apk_url'      => (string)$row['apk_url'],
            'apk_size'     => $row['apk_size'] !== null ? (int)$row['apk_size'] : null,
            'apk_sha256'   => $row['apk_sha256'] !== null ? (string)$row['apk_sha256'] : null,
            'force_update' => $force,
        ]]]);
    }
}
