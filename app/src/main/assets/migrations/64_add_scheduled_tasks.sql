-- 定时任务：按固定间隔把提示词交给 Agent 自主执行。
-- 用间隔分钟数而非 cron：WorkManager 只有 15 分钟粒度且会被省电策略延后。
CREATE TABLE IF NOT EXISTS `scheduled_tasks` (
    `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    `title` TEXT NOT NULL,
    `prompt` TEXT NOT NULL,
    `projectRoot` TEXT,
    `intervalMinutes` INTEGER NOT NULL DEFAULT 60,
    `enabled` INTEGER NOT NULL DEFAULT 1,
    `nextRunAt` INTEGER NOT NULL DEFAULT 0,
    `lastRunAt` INTEGER,
    `lastStatus` TEXT,
    `lastMessage` TEXT,
    `lastSessionId` TEXT,
    `createdAt` INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS `index_scheduled_tasks_enabled_nextRunAt`
    ON `scheduled_tasks` (`enabled`, `nextRunAt`);
