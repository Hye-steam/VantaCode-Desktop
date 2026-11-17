<?php
/**
 * 鉴权：
 * - 设备请求：HMAC-SHA256(原始 body, hmac_secret)，头 X-Device-Id + X-Signature。
 * - 管理面板：Session 登录（password_hash / password_verify）。
 */
declare(strict_types=1);

require_once __DIR__ . '/Support/Audit.php';

final class Auth
{
    /**
     * 校验设备请求签名，返回 ['device_id' => ..., 'body' => array]。
     * 校验失败直接输出 401 并结束请求。
     */
    public static function verifyDevice(string $secret): array
    {
        $signature = (string)($_SERVER['HTTP_X_SIGNATURE'] ?? '');
        $deviceId  = (string)($_SERVER['HTTP_X_DEVICE_ID'] ?? '');

        if ($signature === '' || $deviceId === '') {
            Audit::log($deviceId, 'sign_missing', 'missing X-Device-Id or X-Signature');
            json_out(['code' => 401, 'msg' => 'missing auth headers'], 401);
        }
        if (!preg_match('/^[0-9a-zA-Z\-]{8,64}$/', $deviceId)) {
            Audit::log($deviceId, 'sign_failed', 'malformed device id');
            json_out(['code' => 401, 'msg' => 'invalid device id'], 401);
        }

        // 可选时间戳容差校验，防重放
        $tolerance = (int)cfg('signature_tolerance', 0);
        if ($tolerance > 0) {
            $ts = (string)($_SERVER['HTTP_X_TIMESTAMP'] ?? '');
            if ($ts !== '' && ctype_digit($ts) && abs(time() - (int)$ts) > $tolerance) {
                Audit::log($deviceId, 'sign_expired', 'timestamp out of window');
                json_out(['code' => 401, 'msg' => 'signature expired'], 401);
            }
        }

        $raw = (string)file_get_contents('php://input');
        $expected = hash_hmac('sha256', $raw, $secret);
        if (!hash_equals($expected, strtolower($signature))) {
            Audit::log($deviceId, 'sign_failed', 'hmac mismatch');
            json_out(['code' => 401, 'msg' => 'invalid signature'], 401);
        }

        $decoded = json_decode($raw, true);
        return [
            'device_id' => $deviceId,
            'body'      => is_array($decoded) ? $decoded : [],
        ];
    }

    // ------------------------------------------------------------------ 管理面板

    public static function adminSessionKey(): string
    {
        return (string)cfg('admin_session_key', 'aigw_admin_sess');
    }

    public static function startSession(): void
    {
        if (session_status() === PHP_SESSION_ACTIVE) {
            return;
        }
        session_name(self::adminSessionKey());
        session_set_cookie_params([
            'lifetime' => 0,
            'path'     => '/',
            'httponly' => true,
            'secure'   => !empty($_SERVER['HTTPS']),
            'samesite' => 'Lax',
        ]);
        session_start();
    }

    public static function attemptLogin(string $username, string $password): bool
    {
        $st = db()->prepare('SELECT id, username, password_hash FROM admins WHERE username = ? LIMIT 1');
        $st->execute([$username]);
        $row = $st->fetch();
        if (!$row || !password_verify($password, (string)$row['password_hash'])) {
            Audit::log('', 'admin_login_failed', 'user=' . substr($username, 0, 50));
            return false;
        }
        self::startSession();
        session_regenerate_id(true);
        $_SESSION['admin_id'] = (int)$row['id'];
        $_SESSION['admin_name'] = (string)$row['username'];
        $_SESSION['admin_seen_at'] = time();
        Audit::log('', 'admin_login_ok', 'user=' . substr($username, 0, 50));
        return true;
    }

    /**
     * 会话超时：超过 idle 秒无操作则失效。
     * 防止「登录一次永久有效」—共享电脑或 cookie 泄漏后风险最大。
     */
    public static function adminId(): ?int
    {
        self::startSession();
        $id = $_SESSION['admin_id'] ?? null;
        if (!is_int($id)) {
            return null;
        }
        $idle = (int)cfg('admin_session_idle', 7200);
        $seen = (int)($_SESSION['admin_seen_at'] ?? 0);
        if ($idle > 0 && $seen > 0 && time() - $seen > $idle) {
            self::logout();
            return null;
        }
        $_SESSION['admin_seen_at'] = time();
        return $id;
    }

    public static function logout(): void
    {
        self::startSession();
        $_SESSION = [];
        if (ini_get('session.use_cookies')) {
            $params = session_get_cookie_params();
            setcookie(session_name(), '', time() - 42000, $params['path'], $params['domain'] ?? '', (bool)$params['secure'], (bool)$params['httponly']);
        }
        session_destroy();
    }

    public static function requireAdmin(): int
    {
        $id = self::adminId();
        if ($id === null) {
            json_out(['code' => 401, 'msg' => 'login required'], 401);
        }
        return $id;
    }

    public static function createAdmin(string $username, string $password): void
    {
        db()->prepare('INSERT INTO admins (username, password_hash) VALUES (?, ?)')
            ->execute([$username, password_hash($password, PASSWORD_DEFAULT)]);
    }

    /** 是否还没有任何管理员（用于引导首次初始化）。 */
    public static function hasAnyAdmin(): bool
    {
        return (int)db()->query('SELECT COUNT(*) FROM admins')->fetchColumn() > 0;
    }

    /**
     * 首次初始化：仅当 admins 表为空时允许创建，防止被恶意抢先建号。
     */
    public static function bootstrapFirstAdmin(string $username, string $password): void
    {
        if (self::hasAnyAdmin()) {
            throw new RuntimeException('admin already exists');
        }
        self::createAdmin($username, $password);
        Audit::log('', 'admin_bootstrapped', 'user=' . substr($username, 0, 50));
    }
}
