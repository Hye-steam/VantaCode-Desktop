<?php
/**
 * E3 异常告警巡检（建议每 10 分钟执行）。
 *
 * 规则：running=1 但最后心跳早于 N 小时 → webhook 通知。
 *
 * crontab: *\/10 * * * * php /path/to/aigw-admin/cron/alert.php
 */
declare(strict_types=1);

require __DIR__ . '/../src/bootstrap.php';

$silentHours = 6;
$since = date('Y-m-d H:i:s', time() - $silentHours * 3600);

$st = db()->prepare(
    'SELECT device_id, app_version, last_seen_at FROM devices
     WHERE running = 1 AND last_seen_at IS NOT NULL AND last_seen_at < ?
     ORDER BY last_seen_at ASC LIMIT 100'
);
$st->execute([$since]);
$rows = $st->fetchAll();

if (!$rows) {
    echo '[alert] no silent devices' . PHP_EOL;
    exit(0);
}

$webhook = (string)(setting('alert_webhook', '') ?? '');
if ($webhook === '') {
    echo '[alert] ' . count($rows) . ' silent device(s), but alert_webhook not configured' . PHP_EOL;
    exit(0);
}

$payload = json_encode([
    'alert'   => 'device_silent',
    'hours'   => $silentHours,
    'count'   => count($rows),
    'devices' => $rows,
], JSON_UNESCAPED_UNICODE);

$context = stream_context_create([
    'http' => [
        'method'        => 'POST',
        'header'        => "Content-Type: application/json\r\n",
        'content'       => $payload,
        'timeout'       => 10,
        'ignore_errors' => true,
    ],
]);

$ok = @file_get_contents($webhook, false, $context);
echo '[alert] notified ' . count($rows) . ' device(s): ' . ($ok === false ? 'failed' : 'ok') . PHP_EOL;
