<?php
/** F1 Bug 反馈提交。 */
declare(strict_types=1);

final class BugController
{
    private const SEVERITIES = ['low', 'mid', 'high'];

    public function submit(array $ctx): void
    {
        $deviceId = $ctx['device_id'];
        $body = $ctx['body'];

        $title = self::text($body['title'] ?? '', 300);
        if ($title === '') {
            json_out(['code' => 400, 'msg' => 'title required'], 400);
        }
        $severity = is_string($body['severity'] ?? null) && in_array($body['severity'], self::SEVERITIES, true)
            ? $body['severity']
            : 'mid';

        db()->prepare(
            'INSERT INTO bug_reports (device_id, app_version, title, detail, log_excerpt, severity, status)
             VALUES (?, ?, ?, ?, ?, ?, \'open\')'
        )->execute([
            $deviceId,
            self::text($body['app_version'] ?? '', 20),
            $title,
            self::text($body['detail'] ?? '', 5000),
            self::text($body['log_excerpt'] ?? '', 8000),
            $severity,
        ]);

        json_out(['code' => 0, 'data' => ['id' => (int)db()->lastInsertId()]]);
    }

    private static function text(mixed $value, int $maxLen): string
    {
        if (!is_scalar($value)) {
            return '';
        }
        return trim(mb_substr((string)$value, 0, $maxLen));
    }
}
