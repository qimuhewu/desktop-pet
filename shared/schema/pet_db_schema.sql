-- 双端一致的 SQLite 表结构（Android Room / iOS 对照实现）
-- 规则（docs/04 §2）：改表必须先改本文件；禁止单端私自加列。
-- 时间列均为 Unix 毫秒时间戳。

CREATE TABLE IF NOT EXISTS pet_profile (
    id             TEXT    PRIMARY KEY NOT NULL,  -- 固定 'default_pet'（第一版单宠物）
    name           TEXT    NOT NULL,              -- 宠物名，默认 '小希'
    level          INTEGER NOT NULL DEFAULT 1,    -- 等级
    exp            INTEGER NOT NULL DEFAULT 0,    -- 当前级内经验
    hunger         REAL    NOT NULL DEFAULT 80,   -- 饥饿度 0~100（Double 存储，见 docs/04 §1.5）
    mood           REAL    NOT NULL DEFAULT 60,   -- 心情值 0~100
    intimacy       REAL    NOT NULL DEFAULT 50,   -- 亲密度 0~100
    last_active_at INTEGER NOT NULL,              -- 上次互动时间（惰性衰减基准）
    created_at     INTEGER NOT NULL               -- 创建时间
);

CREATE TABLE IF NOT EXISTS reminder_config (
    id               TEXT    PRIMARY KEY NOT NULL, -- UUID
    template_key     TEXT    NOT NULL,             -- 对应 reminder_templates.json 的 key
    interval_minutes INTEGER NOT NULL DEFAULT 30,  -- 重复间隔（分钟）
    message          TEXT    NOT NULL,             -- 提醒文案
    enabled          INTEGER NOT NULL DEFAULT 1,   -- 1=启用 0=停用
    next_trigger_at  INTEGER                       -- 下次触发时间，NULL=从未排程
);
