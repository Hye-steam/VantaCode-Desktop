<?php
/**
 * G2 数据保留期清理（每日执行）。
 *
 * crontab: 0 3 * * * php /path/to/aigw-admin/cron/cleanup.php
 */
declare(strict_types=1);

require __DIR__ . '/../src/bootstrap.php';

$days = cfg('retention', ['devices_days' => 90, 'reads_days' => 90, 'audit_days' => 30]);
$pdo = db();

$deleted = [];

$st = $pdo->prepare('DELETE FROM devices WHERE last_seen_at IS NOT NULL AND last_seen_at < NOW() - INTERVAL ? DAY');
$st->execute([(int)$days['devices_days']]);
$deleted['devices'] = $st->rowCount();

$st = $pdo->prepare('DELETE FROM announcement_reads WHERE read_at < NOW() - INTERVAL ? DAY');
$st->execute([(int)$days['reads_days']]);
$deleted['announcement_reads'] = $st->rowCount();

$st = $pdo->prepare('DELETE FROM audit_logs WHERE created_at < NOW() - INTERVAL ? DAY');
$st->execute([(int)$days['audit_days']]);
$deleted['audit_logs'] = $st->rowCount();

// 已下线（无对应设备）的同步快照与开关一并清理
$pdo->exec('DELETE s FROM provider_syncs s LEFT JOIN devices d ON d.device_id = s.device_id WHERE d.device_id IS NULL');
$pdo->exec('DELETE s FROM provider_switches s LEFT JOIN devices d ON d.device_id = s.device_id WHERE d.device_id IS NULL');

echo '[cleanup] ' . json_encode($deleted, JSON_UNESCAPED_UNICODE) . PHP_EOL;
