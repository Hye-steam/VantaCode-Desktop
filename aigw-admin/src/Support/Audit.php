<?php
/** 审计日志：记录验签失败、敏感字段泄漏尝试、限流等异常事件。 */
declare(strict_types=1);

final class Audit
{
    public static function log(string $deviceId, string $event, string $detail = ''): void
    {
        try {
            db()->prepare('INSERT INTO audit_logs (device_id, event, detail, ip) VALUES (?, ?, ?, ?)')
                ->execute([
                    substr($deviceId, 0, 32),
                    substr($event, 0, 50),
                    substr($detail, 0, 2000),
                    substr((string)($_SERVER['REMOTE_ADDR'] ?? ''), 0, 45),
                ]);
        } catch (Throwable $e) {
            error_log('[aigw-admin] audit log failed: ' . $e->getMessage());
        }
    }
}
