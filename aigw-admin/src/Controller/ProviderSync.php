<?php
/** D1 供应商授权状态上报（脱敏 JSON），D2 由 Guard 过滤。 */
declare(strict_types=1);

require_once __DIR__ . '/../Guard.php';

final class ProviderSyncController
{
    public function handle(array $ctx): void
    {
        $deviceId = $ctx['device_id'];
        $raw = $ctx['body']['providers'] ?? [];
        if (!is_array($raw)) {
            json_out(['code' => 400, 'msg' => 'providers must be an array'], 400);
        }

        try {
            $clean = Guard::sanitizeProviders($raw);
        } catch (RuntimeException $e) {
            Audit::log($deviceId, 'leak_attempt', $e->getMessage());
            json_out(['code' => 400, 'msg' => 'payload rejected'], 400);
        }

        db()->prepare(
            'INSERT INTO provider_syncs (device_id, sync_json, received_at) VALUES (?, ?, NOW())
             ON DUPLICATE KEY UPDATE sync_json = VALUES(sync_json), received_at = NOW()'
        )->execute([$deviceId, json_encode(['providers' => $clean], JSON_UNESCAPED_UNICODE)]);

        json_out(['code' => 0, 'data' => ['accepted' => count($clean)]]);
    }
}
