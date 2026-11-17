<?php
/** A2 设备注册：记录设备指纹，首次出现时写入 devices 与 device_meta。 */
declare(strict_types=1);

final class DeviceController
{
    public function register(array $ctx): void
    {
        $deviceId = $ctx['device_id'];
        $body = $ctx['body'];
        $now = date('Y-m-d H:i:s');

        $meta = is_array($body['device'] ?? null) ? $body['device'] : [];
        $manufacturer = self::short($meta['manufacturer'] ?? '', 50);
        $model        = self::short($meta['model'] ?? '', 100);
        $android      = self::short($meta['android_version'] ?? '', 20);

        db()->prepare(
            'INSERT INTO devices (device_id, app_version, version_code, first_seen_at, last_seen_at)
             VALUES (?, ?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE app_version = VALUES(app_version), version_code = VALUES(version_code), last_seen_at = VALUES(last_seen_at)'
        )->execute([
            $deviceId,
            self::short($body['app_version'] ?? '', 20),
            (int)($body['version_code'] ?? 0),
            $now,
            $now,
        ]);

        db()->prepare(
            'INSERT INTO device_meta (device_id, manufacturer, model, android_version)
             VALUES (?, ?, ?, ?)
             ON DUPLICATE KEY UPDATE manufacturer = VALUES(manufacturer), model = VALUES(model), android_version = VALUES(android_version)'
        )->execute([$deviceId, $manufacturer, $model, $android]);

        json_out(['code' => 0, 'data' => ['registered' => true]]);
    }

    private static function short(mixed $value, int $maxLen): string
    {
        if (!is_scalar($value)) {
            return '';
        }
        return mb_substr(trim((string)$value), 0, $maxLen);
    }
}
