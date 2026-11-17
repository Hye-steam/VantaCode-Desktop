<?php
/**
 * 创建管理员账号。
 *
 * 用法：php bin/create_admin.php <username> <password>
 */
declare(strict_types=1);

require __DIR__ . '/../src/bootstrap.php';
require __DIR__ . '/../src/Auth.php';

if ($argc < 3) {
    fwrite(STDERR, "用法: php bin/create_admin.php <username> <password>\n");
    exit(1);
}

[$script, $username, $password] = $argv;
$username = trim($username);
if ($username === '' || strlen($password) < 8) {
    fwrite(STDERR, "用户名不能为空，密码至少 8 位\n");
    exit(1);
}

Auth::createAdmin($username, $password);
echo "已创建管理员：{$username}\n";
