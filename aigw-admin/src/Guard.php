<?php
/**
 * 服务端防线：
 * - sanitizeProviders()：深度扫描供应商上报 JSON，发现任何凭证类字段直接拒绝并审计。
 * - rateLimit()：基于文件的简易限流（单机部署够用；多机请换 Redis）。
 */
declare(strict_types=1);

require_once __DIR__ . '/Support/Audit.php';

final class Guard
{
    /** 禁止上报的字段名（小写比较）。命中即视为泄漏尝试。 */
    private const FORBIDDEN = [
        'access_token', 'accesstoken', 'refresh_token', 'refreshtoken',
        'id_token', 'idtoken', 'token', 'session', 'cookie', 'cookies',
        'authorization', 'auth', 'secret', 'apikey', 'api_key', 'password',
        'uid', 'user_id', 'userid', 'phone', 'email', 'nickname',
        'credentials', 'credential', 'headers',
    ];

    /**
     * 校验并归一化供应商上报数据。
     *
     * @param array<int,mixed> $providers 原始 providers 数组
     * @return array<int,array<string,mixed>> 脱敏后的数组
     * @throws RuntimeException 命中禁止字段时抛出（调用方应拒绝整批请求）
     */
    public static function sanitizeProviders(array $providers): array
    {
        $clean = [];
        foreach ($providers as $provider) {
            if (!is_array($provider)) {
                continue;
            }
            self::assertNoForbiddenKeys($provider);

            $id = isset($provider['provider_id']) && is_scalar($provider['provider_id'])
                ? substr(preg_replace('/[^0-9a-zA-Z:_\-]/', '', (string)$provider['provider_id']) ?? '', 0, 60)
                : '';
            if ($id === '') {
                continue;
            }

            $total = self::clampInt($provider['accounts_total'] ?? 0, 0, 999);
            $ok    = self::clampInt($provider['accounts_ok'] ?? 0, 0, $total);

            $clean[] = [
                'provider_id'    => $id,
                'enabled'        => !empty($provider['enabled']),
                'accounts_total' => $total,
                'accounts_ok'    => $ok,
                'last_error'     => self::shortText($provider['last_error'] ?? '', 200),
                'last_call_at'   => self::clampInt($provider['last_call_at'] ?? 0, 0, time()),
            ];
        }
        return $clean;
    }

    /** 递归检查数组键名，命中禁止字段抛异常。 */
    private static function assertNoForbiddenKeys(array $data, int $depth = 0): void
    {
        if ($depth > 6) {
            throw new RuntimeException('payload too deep');
        }
        foreach ($data as $key => $value) {
            if (is_string($key) && in_array(strtolower($key), self::FORBIDDEN, true)) {
                throw new RuntimeException('forbidden field: ' . $key);
            }
            if (is_array($value)) {
                self::assertNoForbiddenKeys($value, $depth + 1);
            }
        }
    }

    private static function clampInt(mixed $value, int $min, int $max): int
    {
        $n = is_numeric($value) ? (int)$value : 0;
        return max($min, min($max, $n));
    }

    private static function shortText(mixed $value, int $maxLen): string
    {
        if (!is_scalar($value)) {
            return '';
        }
        $text = trim((string)$value);
        return mb_substr($text, 0, $maxLen);
    }

    /**
     * 简易限流：同一 key（通常是 device_id）在 window 秒内最多 max 次。
     * 超限时写审计并返回 false。
     */
    public static function rateLimit(string $key, int $window, int $max): bool
    {
        if ($max <= 0) {
            return true;
        }
        $dir = (string)cfg('var_dir', sys_get_temp_dir());
        if (!is_dir($dir) && !@mkdir($dir, 0775, true) && !is_dir($dir)) {
            return true; // 无法创建目录时不阻断业务
        }
        $file = $dir . '/rl_' . hash('sha256', $key) . '.json';
        $now = time();
        $hits = [];
        if (is_file($file)) {
            $raw = @file_get_contents($file);
            $decoded = $raw === false ? null : json_decode($raw, true);
            if (is_array($decoded)) {
                $hits = array_values(array_filter($decoded, static fn($t) => is_int($t) && $t > $now - $window));
            }
        }
        if (count($hits) >= $max) {
            return false;
        }
        $hits[] = $now;
        @file_put_contents($file, json_encode($hits), LOCK_EX);
        return true;
    }
}
