<?php
/**
 * 配置：请勿把真实密钥提交到仓库。
 *
 * 所有密钥优先从环境变量读取；数据库密码必须通过 DB_PASS 提供。
 * 用法（nginx + php-fpm）：
 *   AIGW_HMAC_SECRET=... DB_PASS=... DB_USER=aigw DB_NAME=aigw_admin php-fpm
 */

declare(strict_types=1);

$hmac = getenv('AIGW_HMAC_SECRET');
if ($hmac === false || strlen($hmac) < 32) {
    $message = 'AIGW_HMAC_SECRET 未设置或长度不足 32 字节';
    if (PHP_SAPI === 'cli') {
        fwrite(STDERR, $message . PHP_EOL);
        exit(1);
    }
    http_response_code(500);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['code' => 500, 'msg' => $message], JSON_UNESCAPED_UNICODE);
    exit;
}

return [
    'hmac_secret' => $hmac,

    'db' => [
        'host'    => getenv('DB_HOST') ?: '127.0.0.1',
        'port'    => (int)(getenv('DB_PORT') ?: 3306),
        'name'    => getenv('DB_NAME') ?: 'aigw_admin',
        'user'    => getenv('DB_USER') ?: 'aigw',
        'pass'    => getenv('DB_PASS') ?: '',
        'charset' => 'utf8mb4',
    ],

    'admin_session_key' => 'aigw_admin_sess',

    /** 管理会话空闲超时（秒）。0 = 不超时（不建议）。 */
    'admin_session_idle' => 7200,

    /** 上报限流：同一 device_id 在 window 秒内最多 max 次写请求。 */
    'rate_limit' => ['window' => 10, 'max' => 6],

    /** HMAC 时间戳容差（秒）。大于 0 时校验 X-Timestamp（可选头）。 */
    'signature_tolerance' => 300,

    /** 数据保留天数（cron/cleanup.php 使用）。 */
    'retention' => ['devices_days' => 90, 'reads_days' => 90, 'audit_days' => 30],

    /** 运行期可写目录（限流计数、下载校验缓存等）。 */
    'var_dir' => __DIR__ . '/var',
];
