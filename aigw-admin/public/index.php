<?php
/**
 * 前端控制器（nginx 根目录指向本目录）。
 *
 * - /api/v1/*       APP 端接口（HMAC 签名保护）
 * - /panel/api/*    管理面板接口（Session 保护）
 * - /panel/*        管理面板静态页面
 */
declare(strict_types=1);

require __DIR__ . '/../src/bootstrap.php';
require __DIR__ . '/../src/Auth.php';
require __DIR__ . '/../src/Guard.php';
require __DIR__ . '/../src/Controller/Device.php';
require __DIR__ . '/../src/Controller/Heartbeat.php';
require __DIR__ . '/../src/Controller/Announcement.php';
require __DIR__ . '/../src/Controller/Release.php';
require __DIR__ . '/../src/Controller/ProviderSync.php';
require __DIR__ . '/../src/Controller/Bug.php';

$path = trim((string)parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH), '/');
$method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');

// ---------------------------------------------------------------- 管理面板 API
if (str_starts_with($path, 'panel/api/')) {
    require __DIR__ . '/../src/Controller/Panel.php';
    Panel::dispatch($path, $method);
}

// ---------------------------------------------------------------- 管理面板静态页
if ($path === 'panel' || $path === 'panel/') {
    header('Location: /panel/index.html');
    exit;
}

// ---------------------------------------------------------------- APP 端 API
$routes = [
    'POST api/v1/register'           => 'register',
    'POST api/v1/heartbeat'          => 'heartbeat',
    'GET api/v1/announcements'       => 'announcements',
    'POST api/v1/announcements/read' => 'announcementRead',
    'GET api/v1/release/check'       => 'releaseCheck',
    'POST api/v1/sync/providers'     => 'providerSync',
    'POST api/v1/bugs'               => 'bugReport',
];

$route = $routes["$method $path"] ?? null;
if ($route === null) {
    json_out(['code' => 404, 'msg' => 'not found'], 404);
}

// 读接口（GET）也要求签名：对空 body 做 HMAC
$ctx = Auth::verifyDevice((string)cfg('hmac_secret'));

// 写接口限流（读接口不限）
if ($method === 'POST') {
    $limit = cfg('rate_limit', ['window' => 10, 'max' => 6]);
    if (!Guard::rateLimit($ctx['device_id'], (int)$limit['window'], (int)$limit['max'])) {
        Audit::log($ctx['device_id'], 'rate_limited', $path);
        json_out(['code' => 429, 'msg' => 'too many requests'], 429);
    }
}

match ($route) {
    'register'          => (new DeviceController())->register($ctx),
    'heartbeat'         => (new HeartbeatController())->handle($ctx),
    'announcements'     => (new AnnouncementController())->list($ctx),
    'announcementRead'  => (new AnnouncementController())->markRead($ctx),
    'releaseCheck'      => (new ReleaseController())->check($ctx),
    'providerSync'      => (new ProviderSyncController())->handle($ctx),
    'bugReport'         => (new BugController())->submit($ctx),
};

json_out(['code' => 500, 'msg' => 'unreachable'], 500);
