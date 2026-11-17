<?php
/** B1 公告列表（APP 单独拉取时） + B2 已读上报。 */
declare(strict_types=1);

final class AnnouncementController
{
    /** GET /api/v1/announcements?version_code=xxx —— 供 APP 主动拉取。 */
    public function list(array $ctx): void
    {
        $appCode = (int)($_GET['version_code'] ?? 0);
        $st = db()->prepare(
            'SELECT dedup_key, title, body, body_hash, level, action_label, action_url
             FROM announcements
             WHERE enabled = 1
               AND (starts_at IS NULL OR starts_at <= NOW())
               AND (ends_at   IS NULL OR ends_at   >= NOW())
               AND (min_code  IS NULL OR ? >= min_code)
               AND (max_code  IS NULL OR ? <= max_code)
             ORDER BY FIELD(level, \'critical\', \'warn\', \'info\'), created_at DESC
             LIMIT 20'
        );
        $st->execute([$appCode, $appCode]);
        $items = array_map(static fn(array $r) => [
            'dedup_key'    => (string)$r['dedup_key'],
            'title'        => (string)$r['title'],
            'body'         => (string)$r['body'],
            'body_hash'    => (string)$r['body_hash'],
            'level'        => (string)$r['level'],
            'action_label' => $r['action_label'] !== null ? (string)$r['action_label'] : null,
            'action_url'   => $r['action_url'] !== null ? (string)$r['action_url'] : null,
        ], $st->fetchAll());
        json_out(['code' => 0, 'data' => ['announcements' => $items]]);
    }

    /** POST /api/v1/announcements/read —— 上报已读（body: {dedup_key}）。 */
    public function markRead(array $ctx): void
    {
        $deviceId = $ctx['device_id'];
        $dedupKey = is_scalar($ctx['body']['dedup_key'] ?? null)
            ? substr(trim((string)$ctx['body']['dedup_key']), 0, 100)
            : '';
        if ($dedupKey === '') {
            json_out(['code' => 400, 'msg' => 'dedup_key required'], 400);
        }
        db()->prepare(
            'INSERT INTO announcement_reads (device_id, dedup_key, read_at) VALUES (?, ?, NOW())
             ON DUPLICATE KEY UPDATE read_at = VALUES(read_at)'
        )->execute([$deviceId, $dedupKey]);
        json_out(['code' => 0]);
    }
}
