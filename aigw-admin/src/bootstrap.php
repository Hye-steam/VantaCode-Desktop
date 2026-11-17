<?php
/**
 * 引导文件：加载配置、建立 PDO、注册通用响应函数。
 *
 * 所有入口（public/index.php、cron/*.php、bin/*.php）都必须先 require 本文件。
 */

declare(strict_types=1);

$config = require __DIR__ . '/../config.php';

/** 建立 PDO（懒加载，避免未用数据库的请求也建连接）。 */
function db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }
    global $config;
    $db = $config['db'];
    $dsn = sprintf('mysql:host=%s;port=%d;dbname=%s;charset=%s', $db['host'], $db['port'], $db['name'], $db['charset']);
    $pdo = new PDO($dsn, $db['user'], $db['pass'], [
        PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES   => false,
    ]);
    return $pdo;
}

/** 输出 JSON 并结束请求。 */
function json_out(array $data, int $code = 200): void
{
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

/** 读取并解析原始请求体；非法 JSON 返回空数组。 */
function request_body(): array
{
    static $cached = null;
    if (is_array($cached)) {
        return $cached;
    }
    $raw = file_get_contents('php://input');
    $decoded = json_decode($raw === false ? '' : $raw, true);
    $cached = is_array($decoded) ? $decoded : [];
    return $cached;
}

/** 读取配置项（支持 a.b.c 形式）。 */
function cfg(string $path, mixed $default = null): mixed
{
    global $config;
    $node = $config;
    foreach (explode('.', $path) as $key) {
        if (!is_array($node) || !array_key_exists($key, $node)) {
            return $default;
        }
        $node = $node[$key];
    }
    return $node;
}

/** 读取全局配置表单项（settings 表）。 */
function setting(string $key, ?string $default = null): ?string
{
    $st = db()->prepare('SELECT v FROM settings WHERE k = ?');
    $st->execute([$key]);
    $value = $st->fetchColumn();
    return ($value === false || $value === null) ? $default : (string)$value;
}

/** 写入全局配置表单项。 */
function setting_set(string $key, string $value): void
{
    db()->prepare('INSERT INTO settings (k, v) VALUES (?, ?) ON DUPLICATE KEY UPDATE v = VALUES(v)')
        ->execute([$key, $value]);
}

set_exception_handler(static function (Throwable $e): void {
    error_log('[aigw-admin] ' . $e->getMessage() . ' @ ' . $e->getFile() . ':' . $e->getLine());
    if (PHP_SAPI === 'cli') {
        fwrite(STDERR, '[aigw-admin] ' . $e->getMessage() . PHP_EOL);
        exit(1);
    }
    json_out(['code' => 500, 'msg' => 'internal error'], 500);
});
