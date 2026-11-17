-- AI 网关后台数据库结构（MySQL 5.7+ / 8.0）
-- 执行：mysql -u root -p < schema.sql

CREATE DATABASE IF NOT EXISTS aigw_admin DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE aigw_admin;

-- 管理员
CREATE TABLE IF NOT EXISTS admins (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 设备
CREATE TABLE IF NOT EXISTS devices (
  device_id CHAR(32) PRIMARY KEY,
  app_version VARCHAR(20),
  version_code INT,
  running TINYINT(1) DEFAULT 0,
  port INT,
  pool_summary JSON,
  first_seen_at DATETIME,
  last_seen_at DATETIME,
  INDEX idx_last_seen (last_seen_at)
) ENGINE=InnoDB;

-- 设备指纹（注册时记录，用于识别刷号）
CREATE TABLE IF NOT EXISTS device_meta (
  device_id CHAR(32) PRIMARY KEY,
  manufacturer VARCHAR(50),
  model VARCHAR(100),
  android_version VARCHAR(20),
  registered_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 远程公告
CREATE TABLE IF NOT EXISTS announcements (
  id INT AUTO_INCREMENT PRIMARY KEY,
  dedup_key VARCHAR(100) NOT NULL,
  title VARCHAR(200) NOT NULL,
  body TEXT NOT NULL,
  body_hash CHAR(64) NOT NULL,
  level ENUM('info','warn','critical') DEFAULT 'info',
  action_label VARCHAR(50) NULL,
  action_url VARCHAR(500) NULL,
  min_code INT NULL,
  max_code INT NULL,
  enabled TINYINT(1) DEFAULT 1,
  starts_at DATETIME NULL,
  ends_at DATETIME NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_dedup (dedup_key),
  INDEX idx_enabled_version (enabled, min_code, max_code)
) ENGINE=InnoDB;

-- 公告已读
CREATE TABLE IF NOT EXISTS announcement_reads (
  device_id CHAR(32),
  dedup_key VARCHAR(100),
  read_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (device_id, dedup_key)
) ENGINE=InnoDB;

-- 版本发布
CREATE TABLE IF NOT EXISTS releases (
  id INT AUTO_INCREMENT PRIMARY KEY,
  version VARCHAR(20) NOT NULL UNIQUE,
  version_code INT NOT NULL UNIQUE,
  changelog TEXT NOT NULL,
  apk_url VARCHAR(500) NOT NULL,
  apk_size BIGINT NULL,
  apk_sha256 CHAR(64) NULL,
  force_update TINYINT(1) DEFAULT 0,
  rollout_percent INT DEFAULT 100,
  published_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 全局配置
CREATE TABLE IF NOT EXISTS settings (
  k VARCHAR(100) PRIMARY KEY,
  v TEXT,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 供应商远程开关（设备 × 供应商）
CREATE TABLE IF NOT EXISTS provider_switches (
  device_id CHAR(32),
  provider_id VARCHAR(60),
  enabled TINYINT(1) DEFAULT 1,
  reason VARCHAR(200) NULL,
  updated_by INT NULL,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (device_id, provider_id)
) ENGINE=InnoDB;

-- 供应商同步快照
CREATE TABLE IF NOT EXISTS provider_syncs (
  device_id CHAR(32),
  sync_json JSON,
  received_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (device_id)
) ENGINE=InnoDB;

-- Bug 反馈
CREATE TABLE IF NOT EXISTS bug_reports (
  id INT AUTO_INCREMENT PRIMARY KEY,
  device_id CHAR(32),
  app_version VARCHAR(20),
  title VARCHAR(300) NOT NULL,
  detail TEXT,
  log_excerpt TEXT,
  severity ENUM('low','mid','high') DEFAULT 'mid',
  status ENUM('open','fixing','done') DEFAULT 'open',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_status (status, created_at)
) ENGINE=InnoDB;

-- API 审计日志
CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  device_id CHAR(32),
  event VARCHAR(50),
  detail TEXT,
  ip VARCHAR(45),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_event (event, created_at)
) ENGINE=InnoDB;

-- 预置配置
INSERT INTO settings (k, v) VALUES
  ('min_supported_code', '0'),
  ('heartbeat_interval_sec', '60'),
  ('alert_webhook', '')
ON DUPLICATE KEY UPDATE v = VALUES(v);
