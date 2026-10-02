-- =============================================================================
-- 变电站 · 设备巡检与两票管理  ——  数据库结构与种子数据
-- 说明：本文件在【构建期】于 MySQL 容器里执行，因此只能有普通 DDL / DML，
--       不能出现 DELIMITER 或 CREATE PROCEDURE（存储过程见 backend procedures.sql）。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `substation_ops`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `substation_ops`;

SET NAMES utf8mb4;

DROP TABLE IF EXISTS `work_ticket_log`;
DROP TABLE IF EXISTS `safety_measure`;
DROP TABLE IF EXISTS `operation_step`;
DROP TABLE IF EXISTS `operation_ticket`;
DROP TABLE IF EXISTS `work_ticket`;
DROP TABLE IF EXISTS `defect`;
DROP TABLE IF EXISTS `inspection_record`;
DROP TABLE IF EXISTS `inspection_plan`;
DROP TABLE IF EXISTS `equipment`;
DROP TABLE IF EXISTS `bay`;
DROP TABLE IF EXISTS `substation`;

-- ---------------------------- 1. 变电站 ----------------------------
CREATE TABLE `substation` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `code`            VARCHAR(32)  NOT NULL COMMENT '站代码',
    `name`            VARCHAR(64)  NOT NULL COMMENT '变电站名称',
    `area`            VARCHAR(64)  DEFAULT NULL COMMENT '所属片区',
    `dispatch_org`    VARCHAR(64)  DEFAULT NULL COMMENT '调度单位',
    `watch_mode`      VARCHAR(32)  DEFAULT NULL COMMENT '值守方式：有人值守/无人值守',
    `commission_date` DATE         DEFAULT NULL COMMENT '投运日期',
    `remark`          VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_substation_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='变电站';

-- ---------------------------- 2. 间隔 ----------------------------
CREATE TABLE `bay` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `substation_id` BIGINT       NOT NULL COMMENT '所属变电站',
    `code`          VARCHAR(32)  NOT NULL COMMENT '间隔编号',
    `name`          VARCHAR(64)  NOT NULL COMMENT '间隔名称',
    `bay_type`      VARCHAR(32)  NOT NULL COMMENT '间隔类型：MAIN_TRANSFORMER/OUTGOING/CAPACITOR/BUS_TIE',
    `voltage_level` VARCHAR(16)  NOT NULL COMMENT '电压等级：110kV/35kV/10kV',
    `status`        VARCHAR(24)  NOT NULL COMMENT '间隔状态：RUNNING/HOT_STANDBY/COLD_STANDBY/MAINTENANCE',
    `sort_no`       INT          DEFAULT NULL COMMENT '平面示意图排序位',
    `remark`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_bay_substation` (`substation_id`),
    KEY `idx_bay_status` (`status`),
    UNIQUE KEY `uk_bay_code` (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='间隔';

-- ---------------------------- 3. 设备台账 ----------------------------
CREATE TABLE `equipment` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `bay_id`          BIGINT       NOT NULL COMMENT '所属间隔',
    `equipment_no`    VARCHAR(32)  NOT NULL COMMENT '设备编号',
    `name`            VARCHAR(64)  NOT NULL COMMENT '设备名称',
    `equip_type`      VARCHAR(32)  NOT NULL COMMENT '设备类型：TRANSFORMER/BREAKER/DISCONNECTOR/CT_PT/ARRESTER',
    `model`           VARCHAR(64)  DEFAULT NULL COMMENT '型号',
    `vendor`          VARCHAR(64)  DEFAULT NULL COMMENT '生产厂家',
    `commission_date` DATE         DEFAULT NULL COMMENT '投运日期',
    `last_check_date` DATE         DEFAULT NULL COMMENT '上次检修日期',
    `status`          VARCHAR(24)  NOT NULL COMMENT '设备状态：RUNNING/STANDBY/MAINTENANCE/DEFECTIVE',
    `remark`          VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_equipment_bay` (`bay_id`),
    UNIQUE KEY `uk_equipment_no` (`equipment_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='设备台账';

-- ---------------------------- 4. 巡检计划 ----------------------------
CREATE TABLE `inspection_plan` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `bay_id`      BIGINT       NOT NULL COMMENT '巡检间隔',
    `plan_date`   DATE         NOT NULL COMMENT '计划日期',
    `shift`       VARCHAR(16)  NOT NULL COMMENT '班次：DAY/NIGHT',
    `patrol_type` VARCHAR(24)  DEFAULT NULL COMMENT '巡检性质：例行/特巡/夜巡/保电',
    `inspector`   VARCHAR(32)  DEFAULT NULL COMMENT '值班负责人',
    `status`      VARCHAR(24)  NOT NULL COMMENT '计划状态：PENDING/DONE/DELAYED',
    `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_plan_date` (`plan_date`),
    KEY `idx_plan_bay` (`bay_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='巡检计划';

-- ---------------------------- 5. 巡检记录 ----------------------------
CREATE TABLE `inspection_record` (
    `id`             BIGINT         NOT NULL AUTO_INCREMENT,
    `plan_id`        BIGINT         DEFAULT NULL COMMENT '对应巡检计划',
    `bay_id`         BIGINT         NOT NULL COMMENT '巡检间隔',
    `check_time`     DATETIME       NOT NULL COMMENT '巡检完成时间',
    `inspector`      VARCHAR(32)    DEFAULT NULL COMMENT '巡检人',
    `result_type`    VARCHAR(24)    NOT NULL COMMENT '结论：NORMAL/ABNORMAL',
    `load_rate`      DECIMAL(6, 2)  DEFAULT NULL COMMENT '当日负荷率(%)',
    `max_temperature` DECIMAL(6, 2) DEFAULT NULL COMMENT '红外测温最高点(℃)',
    `remark`         VARCHAR(255)   DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_record_time` (`check_time`),
    KEY `idx_record_bay` (`bay_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='巡检记录';

-- ---------------------------- 6. 缺陷 ----------------------------
CREATE TABLE `defect` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `bay_id`       BIGINT       NOT NULL COMMENT '缺陷间隔',
    `equipment_id` BIGINT       DEFAULT NULL COMMENT '缺陷设备',
    `defect_no`    VARCHAR(32)  NOT NULL COMMENT '缺陷单号',
    `title`        VARCHAR(128) NOT NULL COMMENT '缺陷现象',
    `description`  VARCHAR(512) DEFAULT NULL COMMENT '缺陷描述',
    `level`        VARCHAR(16)  NOT NULL COMMENT '等级：URGENT/MAJOR/MINOR',
    `found_time`   DATETIME     NOT NULL COMMENT '发现时间',
    `reporter`     VARCHAR(32)  DEFAULT NULL COMMENT '发现人',
    `due_date`     DATE         NOT NULL COMMENT '限期消缺日期',
    `owner`        VARCHAR(32)  DEFAULT NULL COMMENT '消缺负责人',
    `status`       VARCHAR(24)  NOT NULL COMMENT '闭环状态：REGISTERED/DISPATCHED/PROCESSING/ACCEPTED',
    `closed_time`  DATETIME     DEFAULT NULL COMMENT '闭环（验收）时间',
    PRIMARY KEY (`id`),
    KEY `idx_defect_bay` (`bay_id`),
    KEY `idx_defect_due` (`due_date`),
    KEY `idx_defect_status` (`status`),
    UNIQUE KEY `uk_defect_no` (`defect_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='缺陷';

-- ---------------------------- 7. 工作票 ----------------------------
CREATE TABLE `work_ticket` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `bay_id`          BIGINT       NOT NULL COMMENT '作业间隔',
    `ticket_no`       VARCHAR(64)  DEFAULT NULL COMMENT '票号（由存储过程生成）',
    `issue_date`      DATE         NOT NULL COMMENT '业务日期',
    `work_leader`     VARCHAR(32)  DEFAULT NULL COMMENT '工作负责人',
    `team_members`    VARCHAR(255) DEFAULT NULL COMMENT '工作班成员',
    `work_content`    VARCHAR(512) DEFAULT NULL COMMENT '工作内容',
    `power_cut_range` VARCHAR(512) DEFAULT NULL COMMENT '停电范围',
    `safety_measures` VARCHAR(512) DEFAULT NULL COMMENT '安全措施',
    `plan_start_time` DATETIME     DEFAULT NULL COMMENT '计划开工时间',
    `plan_end_time`   DATETIME     DEFAULT NULL COMMENT '计划完工时间',
    `issuer`          VARCHAR(32)  DEFAULT NULL COMMENT '签发人',
    `permittor`       VARCHAR(32)  DEFAULT NULL COMMENT '许可人',
    `terminator`      VARCHAR(32)  DEFAULT NULL COMMENT '终结人',
    `issue_time`      DATETIME     DEFAULT NULL COMMENT '签发时间',
    `permit_time`     DATETIME     DEFAULT NULL COMMENT '许可时间',
    `terminate_time`  DATETIME     DEFAULT NULL COMMENT '终结时间',
    `status`          VARCHAR(24)  NOT NULL COMMENT '票面状态：DRAFT/ISSUED/PERMITTED/TERMINATED/CANCELED',
    `remark`          VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_work_date` (`issue_date`),
    KEY `idx_work_bay` (`bay_id`),
    KEY `idx_work_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='工作票（三审）';

-- ---------------------------- 8. 操作票 ----------------------------
CREATE TABLE `operation_ticket` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `bay_id`        BIGINT       NOT NULL COMMENT '操作间隔',
    `ticket_no`     VARCHAR(64)  DEFAULT NULL COMMENT '票号',
    `issue_date`    DATE         NOT NULL COMMENT '业务日期',
    `task_name`     VARCHAR(255) NOT NULL COMMENT '操作任务',
    `dispatcher`    VARCHAR(32)  DEFAULT NULL COMMENT '发令人',
    `guardian`      VARCHAR(32)  DEFAULT NULL COMMENT '监护人',
    `operator_name` VARCHAR(32)  DEFAULT NULL COMMENT '操作人',
    `status`        VARCHAR(24)  NOT NULL COMMENT '票面状态：PENDING/RUNNING/FINISHED/CANCELED',
    `remark`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_op_date` (`issue_date`),
    KEY `idx_op_bay` (`bay_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='操作票';

-- ---------------------------- 9. 操作步骤 ----------------------------
CREATE TABLE `operation_step` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `ticket_id`     BIGINT       NOT NULL COMMENT '所属操作票',
    `step_no`       INT          NOT NULL COMMENT '步骤序号，从 1 开始',
    `action_desc`   VARCHAR(255) NOT NULL COMMENT '操作内容',
    `execute_person` VARCHAR(32) DEFAULT NULL COMMENT '执行人',
    `execute_time`  DATETIME     DEFAULT NULL COMMENT '执行时间',
    `status`        VARCHAR(24)  NOT NULL COMMENT '步骤状态：PENDING/DONE',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_step_ticket_no` (`ticket_id`, `step_no`),
    KEY `idx_step_ticket` (`ticket_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='倒闸操作步骤';

-- ---------------------------- 10. 安全措施（逐项可确认的执行闭环） ----------------------------
-- 每项措施区分类型（接地线/接地刀闸/遮栏/标示牌），记录所在间隔、具体位置、要求动作，
-- 并逐项登记装设（合上）与拆除（拉开）的执行人、执行时间。
-- 阶段：PENDING 待执行 → EXECUTED 已执行（许可后呈现为 PENDING_REMOVE 待拆除）→ REMOVED 已拆除；
--       CANCELED 为现场变更后旧项作废，只作历史痕迹保留，不再参与许可/终结核对。
CREATE TABLE `safety_measure` (
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `ticket_id`           BIGINT       NOT NULL COMMENT '所属工作票',
    `seq_no`              INT          NOT NULL COMMENT '票面措施序号，从 1 开始',
    `measure_type`        VARCHAR(16)  NOT NULL COMMENT '类型：GROUND_WIRE 接地线/EARTH_SWITCH 接地刀闸/BARRIER 遮栏/SIGNBOARD 标示牌',
    `bay_id`              BIGINT       NOT NULL COMMENT '所在间隔',
    `location`            VARCHAR(128) NOT NULL COMMENT '具体位置，如「2017 接地刀闸刀闸口」「主变 10kV 套管侧」',
    `required_action`     VARCHAR(255) NOT NULL COMMENT '要求动作，如「装设接地线 1 组」「合上接地刀闸」「装设遮栏」「悬挂标示牌」',
    `signboard_text`      VARCHAR(64)  DEFAULT NULL COMMENT '标示牌文字，如「禁止合闸，有人工作」「在此工作」',
    `op_ticket_id`        BIGINT       DEFAULT NULL COMMENT '许可前关联的倒闸操作票（装设/合上对应步骤）',
    `op_step_no`          INT          DEFAULT NULL COMMENT '关联的装设（合上）步骤序号',
    `restore_ticket_id`   BIGINT       DEFAULT NULL COMMENT '终结前关联的恢复操作票（拆除/拉开对应步骤）',
    `restore_step_no`     INT          DEFAULT NULL COMMENT '关联的恢复（拆除）步骤序号',
    `status`              VARCHAR(24)  NOT NULL COMMENT '阶段：PENDING/EXECUTED/PENDING_REMOVE/REMOVED/CANCELED',
    `execute_person`      VARCHAR(32)  DEFAULT NULL COMMENT '装设/合上执行人',
    `execute_time`        DATETIME     DEFAULT NULL COMMENT '装设/合上执行时间',
    `remove_person`       VARCHAR(32)  DEFAULT NULL COMMENT '拆除/拉开执行人',
    `remove_time`         DATETIME     DEFAULT NULL COMMENT '拆除/拉开执行时间',
    `change_remark`       VARCHAR(255) DEFAULT NULL COMMENT '现场变更说明（撤回许可后变更/作废时填写）',
    `version`             INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号，防止两人同时确认产生重复执行',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '建项时间',
    PRIMARY KEY (`id`),
    KEY `idx_sm_ticket` (`ticket_id`),
    KEY `idx_sm_bay` (`bay_id`),
    KEY `idx_sm_status` (`status`),
    KEY `idx_sm_opstep` (`op_ticket_id`, `op_step_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='工作票安全措施（逐项确认闭环）';

-- ---------------------------- 11. 工作票操作痕迹（只增不改不删） ----------------------------
-- 撤回许可、现场变更、部分失败后刷新重进，都能据此还原真实阶段；已发生的确认记录永不抹掉。
CREATE TABLE `work_ticket_log` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `ticket_id`   BIGINT       NOT NULL COMMENT '所属工作票',
    `action`      VARCHAR(32)  NOT NULL COMMENT '动作：ISSUE/PERMIT/WITHDRAW/TERMINATE/CANCEL/MEASURE_EXECUTE/MEASURE_REMOVE/MEASURE_ADD/MEASURE_UPDATE/MEASURE_CANCEL',
    `measure_id`  BIGINT       DEFAULT NULL COMMENT '涉及的安全措施（三审动作为空）',
    `person`      VARCHAR(32)  DEFAULT NULL COMMENT '操作人',
    `detail`      VARCHAR(512) DEFAULT NULL COMMENT '动作明细（含措施类型/位置/关联步骤等）',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_wtl_ticket` (`ticket_id`),
    KEY `idx_wtl_time` (`created_at`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='工作票操作痕迹（追加式审计）';

-- =============================================================================
-- 种子数据（日期依托 CURDATE()，保证演示当天时间轴一定有票）
-- =============================================================================

-- 1. 变电站 5 座
INSERT INTO `substation` (`id`, `code`, `name`, `area`, `dispatch_org`, `watch_mode`, `commission_date`, `remark`) VALUES
    (1, 'CD', '城东变电站', '城阳区', '市调', '有人值守', '2009-06-18', '枢纽站，承担城区主供电'),
    (2, 'HX', '河西变电站', '老城区', '区调', '无人值守', '2013-11-02', '老城区负荷中心'),
    (3, 'BL', '白龙变电站', '高新区', '区调', '无人值守', '2018-04-26', '高新区新建站'),
    (4, 'NJ', '南郊变电站', '经开区', '市调', '有人值守', '2006-09-12', '投运最早，设备老化较快'),
    (5, 'XY', '西园变电站', '西城区', '配调', '无人值守', '2021-05-30', '配网末端站');

-- 2. 间隔 12 个（主变 / 出线 / 电容器 / 母联，四种状态齐备）
INSERT INTO `bay` (`id`, `substation_id`, `code`, `name`, `bay_type`, `voltage_level`, `status`, `sort_no`, `remark`) VALUES
    (1,  1, 'CD-1T',  '1号主变间隔',      'MAIN_TRANSFORMER', '110kV', 'RUNNING',      1, '运行中，带全站 60% 负荷'),
    (2,  1, 'CD-2T',  '2号主变间隔',      'MAIN_TRANSFORMER', '110kV', 'MAINTENANCE',  2, '冷却器改造中，已挂接地线'),
    (3,  1, 'CD-1L',  '35kV城东一线',     'OUTGOING',         '35kV',  'RUNNING',      3, NULL),
    (4,  1, 'CD-2L',  '35kV城东二线',     'OUTGOING',         '35kV',  'HOT_STANDBY',  4, '热备用，随时可投入'),
    (5,  1, 'CD-1C',  '10kV I段电容器',   'CAPACITOR',        '10kV',  'RUNNING',      5, '无功补偿'),
    (6,  1, 'CD-BT',  '110kV母联间隔',    'BUS_TIE',          '110kV', 'COLD_STANDBY', 6, '冷备，等待倒母线'),
    (7,  2, 'HX-1T',  '1号主变间隔',      'MAIN_TRANSFORMER', '110kV', 'RUNNING',      7, NULL),
    (8,  2, 'HX-1L',  '35kV河西线',       'OUTGOING',         '35kV',  'COLD_STANDBY', 8, '转冷备待检修'),
    (9,  2, 'HX-2L',  '35kV河西二线',     'OUTGOING',         '35kV',  'MAINTENANCE',  9, '正在更换断路器机构'),
    (10, 2, 'HX-1C',  '10kV II段电容器',  'CAPACITOR',        '10kV',  'RUNNING',     10, NULL),
    (11, 3, 'BL-1T',  '1号主变间隔',      'MAIN_TRANSFORMER', '110kV', 'RUNNING',     11, NULL),
    (12, 3, 'BL-1L',  '35kV白龙线',       'OUTGOING',         '35kV',  'RUNNING',     12, '新投产线路');

-- 3. 设备台账 14 台
INSERT INTO `equipment` (`id`, `bay_id`, `equipment_no`, `name`, `equip_type`, `model`, `vendor`, `commission_date`, `last_check_date`, `status`, `remark`) VALUES
    (1,  1,  'CD-1B',   '1号主变压器',     'TRANSFORMER',  'SZ11-50000/110', '特变电工', '2009-06-18', '2024-05-12', 'RUNNING',    NULL),
    (2,  1,  'CD-101',  '101断路器',       'BREAKER',      'LW36-126',       '平高电气', '2010-03-01', '2025-01-20', 'RUNNING',    NULL),
    (3,  1,  'CD-1011', '1011隔离开关',    'DISCONNECTOR', 'GW4-126',        '如高高压', '2010-03-01', '2022-08-08', 'DEFECTIVE',  '触头曾发热处理'),
    (4,  1,  'CD-1LH',  '110kV电流互感器', 'CT_PT',        'LB6-126',        '思源电气', '2010-03-01', '2023-11-11', 'RUNNING',    NULL),
    (5,  1,  'CD-1BL',  '110kV避雷器',     'ARRESTER',     'Y10W-100/260',   '抚顺电瓷', '2010-03-01', '2026-03-02', 'RUNNING',    NULL),
    (6,  2,  'CD-2B',   '2号主变压器',     'TRANSFORMER',  'SZ11-63000/110', '特变电工', '2012-09-05', '2026-08-15', 'MAINTENANCE', NULL),
    (7,  2,  'CD-201',  '201断路器',       'BREAKER',      'LW36-126',       '平高电气', '2012-09-05', '2026-01-04', 'MAINTENANCE', NULL),
    (8,  3,  'CD-311',  '311断路器',       'BREAKER',      'ZW32-40.5',      '正泰电气', '2014-02-11', '2025-06-30', 'RUNNING',    NULL),
    (9,  3,  'CD-311CT','311电流互感器',   'CT_PT',        'LZZBJ9-40.5',    '思源电气', '2014-02-11', '2021-06-01', 'DEFECTIVE',  '油位异常待处理'),
    (10, 5,  'CD-1CB',  '电容器组进线断路器', 'BREAKER',    'ZN63-12',        '正泰电气', '2015-07-22', '2024-12-19', 'RUNNING',    NULL),
    (11, 6,  'CD-BT1',  '母联断路器',      'BREAKER',      'LW36-126',       '平高电气', '2010-03-01', '2023-04-25', 'STANDBY',    NULL),
    (12, 7,  'HX-1B',   '1号主变压器',     'TRANSFORMER',  'SZ11-40000/110', '山东电工', '2013-11-02', '2024-11-30', 'RUNNING',    NULL),
    (13, 8,  'HX-311',  '311断路器',       'BREAKER',      'ZW32-40.5',      '正泰电气', '2014-05-16', '2023-09-09', 'STANDBY',    NULL),
    (14, 9,  'HX-321',  '321断路器',       'BREAKER',      'ZW32-40.5',      '正泰电气', '2016-08-08', '2026-09-01', 'MAINTENANCE', NULL);

-- 4. 巡检计划 12 条（覆盖今天前后 4 天）
INSERT INTO `inspection_plan` (`id`, `bay_id`, `plan_date`, `shift`, `patrol_type`, `inspector`, `status`, `remark`) VALUES
    (1,  1,  DATE_SUB(CURDATE(), INTERVAL 2 DAY), 'DAY',   '例行', '张伟', 'DONE',    NULL),
    (2,  1,  DATE_SUB(CURDATE(), INTERVAL 2 DAY), 'NIGHT', '夜巡', '马超', 'DONE',    NULL),
    (3,  3,  DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'DAY',   '例行', '李雪', 'DONE',    NULL),
    (4,  5,  DATE_SUB(CURDATE(), INTERVAL 1 DAY), 'DAY',   '例行', '张伟', 'DONE',    NULL),
    (5,  2,  CURDATE(),                           'DAY',   '特巡', '陈国栋', 'PENDING', '配合主变冷却器改造'),
    (6,  1,  CURDATE(),                           'DAY',   '例行', '张伟', 'PENDING', NULL),
    (7,  1,  CURDATE(),                           'NIGHT', '夜巡', '马超', 'PENDING', NULL),
    (8,  7,  CURDATE(),                           'DAY',   '例行', '孙磊', 'PENDING', NULL),
    (9,  8,  CURDATE(),                           'DAY',   '例行', '周涛', 'PENDING', NULL),
    (10, 9,  DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'DAY',   '特巡', '赵刚', 'PENDING', '断路器更换后首次巡检'),
    (11, 11, DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'DAY',   '例行', '吴斌', 'PENDING', NULL),
    (12, 12, DATE_ADD(CURDATE(), INTERVAL 1 DAY), 'NIGHT', '夜巡', '郑楠', 'PENDING', NULL);

-- 5. 巡检记录 10 条
INSERT INTO `inspection_record` (`id`, `plan_id`, `bay_id`, `check_time`, `inspector`, `result_type`, `load_rate`, `max_temperature`, `remark`) VALUES
    (1,  1, 1, CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:20:00'), '张伟', 'NORMAL',   62.30, 48.50, NULL),
    (2,  2, 1, CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 23:40:00'), '马超', 'ABNORMAL', 58.10, 96.20, '1011隔离开关触头红外测温偏高，已登记缺陷'),
    (3,  3, 3, CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 10:05:00'), '李雪', 'ABNORMAL', 71.80, 61.00, '311电流互感器油位低于下限'),
    (4,  4, 5, CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 14:35:00'), '张伟', 'NORMAL',   44.20, 39.80, NULL),
    (5,  6, 1, CONCAT(CURDATE(), ' 08:50:00'),                         '张伟', 'NORMAL',   66.70, 51.30, NULL),
    (6,  7, 1, CONCAT(CURDATE(), ' 22:15:00'),                         '马超', 'ABNORMAL', 52.40, 98.60, '复测触头温度继续升高，建议尽快停电处理'),
    (7,  8, 7, CONCAT(CURDATE(), ' 09:40:00'),                         '孙磊', 'ABNORMAL', 74.50, 68.20, '主变油面温度偏高，已达 78℃'),
    (8,  9, 8, CONCAT(CURDATE(), ' 11:10:00'),                         '周涛', 'NORMAL',   12.60, 32.10, '冷备状态，设备柜体正常'),
    (9,  2, 2, CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 16:20:00'), '陈国栋', 'NORMAL', 8.00,  35.60, '检修间隔，主要核对安措'),
    (10, 5, 3, CONCAT(CURDATE(), ' 15:05:00'),                         '李雪', 'NORMAL',   69.20, 55.40, NULL);

-- 6. 缺陷 12 条（含超期未消缺）
INSERT INTO `defect` (`id`, `bay_id`, `equipment_id`, `defect_no`, `title`, `description`, `level`, `found_time`, `reporter`, `due_date`, `owner`, `status`, `closed_time`) VALUES
    (1,  1,  3,  'CD-QX-001', '1011隔离开关触头过热',   '红外测温 96.2℃，超规程允许值，需打磨触头并处理氧化层', 'URGENT', CONCAT(DATE_SUB(CURDATE(), INTERVAL 5 DAY), ' 23:40:00'), '马超', DATE_SUB(CURDATE(), INTERVAL 3 DAY), NULL,  'REGISTERED', NULL),
    (2,  1,  2,  'CD-QX-002', '101断路器SF6压力偏低',   '密度继电器指示 0.42MPa，接近报警值 0.40MPa',           'MAJOR',  CONCAT(DATE_SUB(CURDATE(), INTERVAL 12 DAY), ' 09:05:00'), '张伟', DATE_SUB(CURDATE(), INTERVAL 5 DAY), '李强', 'DISPATCHED', NULL),
    (3,  3,  9,  'CD-QX-003', '311电流互感器油位异常',  '巡视发现油位低于下限，疑似存在渗漏',                    'MAJOR',  CONCAT(DATE_SUB(CURDATE(), INTERVAL 8 DAY), ' 10:05:00'), '李雪', DATE_SUB(CURDATE(), INTERVAL 1 DAY), '王芳', 'PROCESSING', NULL),
    (4,  5,  10, 'CD-QX-004', '电容器组外壳渗油',       '电容器外壳底部出现油迹，暂不影响运行',                  'MINOR',  CONCAT(DATE_SUB(CURDATE(), INTERVAL 4 DAY), ' 14:35:00'), '张伟', DATE_ADD(CURDATE(), INTERVAL 4 DAY), NULL,  'REGISTERED', NULL),
    (5,  2,  6,  'CD-QX-005', '2号主变冷却器风扇异响',  '第 3 组冷却器风扇轴承异响，运行中噪声明显',            'URGENT', CONCAT(DATE_SUB(CURDATE(), INTERVAL 9 DAY), ' 20:10:00'), '赵刚', DATE_SUB(CURDATE(), INTERVAL 7 DAY), '赵刚', 'PROCESSING', NULL),
    (6,  7,  12, 'HX-QX-006', '主变油面温度计偏高',     '上层油温最高 78℃，冷却系统未见异常，需校核表计',       'MAJOR',  CONCAT(CURDATE(), ' 09:40:00'),                          '孙磊', DATE_ADD(CURDATE(), INTERVAL 2 DAY), '孙磊', 'DISPATCHED', NULL),
    (7,  8,  13, 'HX-QX-007', '311断路器机构箱受潮',    '机构箱内部凝露，已放干燥剂',                           'MINOR',  CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 11:10:00'), '周涛', DATE_ADD(CURDATE(), INTERVAL 15 DAY), NULL, 'REGISTERED', NULL),
    (8,  9,  14, 'HX-QX-008', '321断路器分合闸指示失灵','指示灯不亮，机械位置指示滞后',                          'URGENT', CONCAT(DATE_SUB(CURDATE(), INTERVAL 3 DAY), ' 15:20:00'), '周涛', DATE_SUB(CURDATE(), INTERVAL 2 DAY), '周涛', 'DISPATCHED', NULL),
    (9,  11, NULL,'BL-QX-009', '主变本体法兰渗漏油',    '本体法兰处有油迹，暂未形成滴油',                        'MAJOR',  CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 16:45:00'), '吴斌', DATE_ADD(CURDATE(), INTERVAL 6 DAY), NULL,  'REGISTERED', NULL),
    (10, 4,  NULL,'CD-QX-010', '35kV避雷器计数器破损',  '外绝缘完好，计数器玻璃破碎',                            'MINOR',  CONCAT(DATE_SUB(CURDATE(), INTERVAL 20 DAY), ' 09:30:00'), '李雪', DATE_SUB(CURDATE(), INTERVAL 6 DAY), '郑楠', 'ACCEPTED', CONCAT(DATE_SUB(CURDATE(), INTERVAL 12 DAY), ' 16:00:00')),
    (11, 6,  11, 'CD-QX-011', '母联隔离开关传动杆锈蚀', '传动连杆锈蚀较重，操作阻力大',                          'MAJOR',  CONCAT(DATE_SUB(CURDATE(), INTERVAL 15 DAY), ' 10:15:00'), '张伟', DATE_SUB(CURDATE(), INTERVAL 9 DAY), '郑楠', 'ACCEPTED', CONCAT(DATE_SUB(CURDATE(), INTERVAL 10 DAY), ' 11:20:00')),
    (12, 12, NULL,'BL-QX-012', '电缆终端有放电痕迹',    '35kV出线电缆终端伞裙出现爬电痕迹',                      'URGENT', CONCAT(DATE_SUB(CURDATE(), INTERVAL 6 DAY), ' 21:05:00'), '郑楠', DATE_SUB(CURDATE(), INTERVAL 4 DAY), '吴斌', 'PROCESSING', NULL);

-- 7. 工作票 8 张（含完整三审信息）
INSERT INTO `work_ticket`
(`id`, `bay_id`, `ticket_no`, `issue_date`, `work_leader`, `team_members`, `work_content`, `power_cut_range`, `safety_measures`, `plan_start_time`, `plan_end_time`, `issuer`, `permittor`, `terminator`, `issue_time`, `permit_time`, `terminate_time`, `status`, `remark`) VALUES
(1, 2,  'GZP-CD-01-CD-2T', DATE_SUB(CURDATE(), INTERVAL 2 DAY), '陈国栋', '李强,王芳,刘洋',
 '2号主变冷却器整体改造：更换 3 组冷却器风扇电机、重放二次控制电缆', '2号主变间隔及 201、2011 隔离开关，主变转检修',
 '1.断开201断路器及两侧隔离开关；2.合上2017接地刀闸；3.在主变本体及冷却器周围装设遮栏并悬挂"在此工作"标示牌',
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 08:30:00'), CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 18:00:00'),
 '朱明', '刘玉兰', '黄志强',
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 07:50:00'), CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 08:35:00'), CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 17:40:00'),
 'TERMINATED', '冷却器改造已完成，冷却器联动试验合格'),
(2, 8,  'GZP-HX-01-HX-1L', DATE_SUB(CURDATE(), INTERVAL 1 DAY), '周涛', '孙迪,马亮',
 '35kV河西线 311 断路器机构大修', '35kV河西线 311 断路器间隔，线路转检修',
 '1.拉开311断路器及两侧隔离开关；2.合上3117接地刀闸；3.验电接地；4.设围栏挂标示牌',
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 09:00:00'), CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 17:30:00'),
 '朱明', NULL, NULL, CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 08:20:00'), NULL, NULL,
 'ISSUED', '等待当值许可'),
(3, 9,  'GZP-HX-02-HX-2L', DATE_SUB(CURDATE(), INTERVAL 1 DAY), '赵刚', '张勇,刘洋,孙迪',
 '35kV河西二线 321 断路器更换分合闸线圈', '35kV河西二线 321 断路器间隔，线路转检修',
 '1.拉开321断路器及两侧隔离开关；2.合上3217接地刀闸；3.验电接地并悬挂标示牌',
 CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 13:00:00'), CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 21:00:00'),
 NULL, NULL, NULL, NULL, NULL, NULL,
 'DRAFT', '施工单位已报票，待值长签发'),
(4, 6,  'GZP-CD-02-CD-BT', CURDATE(), '张伟', '李强,郑楠',
 '110kV母联断路器传动机构除锈并处理', '110kV母联间隔，母联断路器及两侧隔离开关',
 '1.断开母联断路器；2.拉开两侧隔离开关；3.合上接地刀闸；4.设围栏挂标示牌',
 CONCAT(CURDATE(), ' 09:00:00'), CONCAT(CURDATE(), ' 16:30:00'),
 '朱明', '刘玉兰', NULL, CONCAT(CURDATE(), ' 08:10:00'), CONCAT(CURDATE(), ' 09:05:00'), NULL,
 'PERMITTED', '已许可开工，进入铲锈作业'),
(5, 2,  'GZP-CD-03-CD-2T', CURDATE(), '王芳', '刘洋,马亮',
 '2号主变更换有载分接开关滤油机滤芯及取油样', '2号主变间隔，主变保持检修状态',
 '1.确认2017接地刀闸在合位；2.验电；3.工作区域设围栏挂标示牌',
 CONCAT(CURDATE(), ' 14:00:00'), CONCAT(CURDATE(), ' 20:00:00'),
 '孙振华', '刘玉兰', '黄志强',
 CONCAT(CURDATE(), ' 13:20:00'), CONCAT(CURDATE(), ' 14:05:00'), CONCAT(CURDATE(), ' 19:30:00'),
 'TERMINATED', '滤芯更换完毕，油样已送检'),
(6, 3,  'GZP-CD-04-CD-1L', CURDATE(), '李雪', '张勇',
 '35kV城东一线 311 电流互感器补油及密封处理', '35kV城东一线间隔（不停电带电作业）',
 '1.保持安全距离不小于 0.7m；2.使用绝缘工具；3.专人监护',
 CONCAT(CURDATE(), ' 10:00:00'), CONCAT(CURDATE(), ' 15:00:00'),
 NULL, NULL, NULL, NULL, NULL, NULL,
 'DRAFT', '带电作业票，待签发'),
(7, 11, 'GZP-BL-01-BL-1T', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '吴斌', '郑楠,马亮,刘洋',
 '1号主变本体法兰渗漏油处理及例行小修', '1号主变间隔，主变转检修',
 '1.断开101断路器及两侧隔离开关；2.合上1017接地刀闸；3.验电接地；4.设围栏挂标示牌',
 CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 08:30:00'), CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 18:00:00'),
 '孙振华', NULL, NULL, CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 07:40:00'), NULL, NULL,
 'ISSUED', '计划性检修，已签发'),
(8, 4,  'GZP-CD-05-CD-2L', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '郑楠', '王芳,孙迪',
 '35kV城东二线热备转运行前的预试—避雷器及电缆头检查', '35kV城东二线间隔（停电作业）',
 '1.确认线路已转冷备；2.验电接地；3.设围栏挂标示牌',
 CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 10:00:00'), CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 16:00:00'),
 '朱明', '刘玉兰', '黄志强',
 CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 09:10:00'), CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 10:20:00'), CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 15:40:00'),
 'TERMINATED', '预试合格，具备投运条件');

-- 8. 操作票 10 张（1-6 为倒闸装设票，7-10 为终结恢复票）
INSERT INTO `operation_ticket` (`id`, `bay_id`, `ticket_no`, `issue_date`, `task_name`, `dispatcher`, `guardian`, `operator_name`, `status`, `remark`) VALUES
    (1, 6,  'CZP-CD-01', DATE_SUB(CURDATE(), INTERVAL 2 DAY), '110kV II段母线由运行转检修（倒负荷至 I 段）', '黄志强', '刘玉兰', '张伟', 'FINISHED', '全部步骤已按序执行'),
    (2, 2,  'CZP-CD-02', DATE_SUB(CURDATE(), INTERVAL 1 DAY), '2号主变由运行转检修',                         '孙振华', '刘玉兰', '陈国栋', 'FINISHED', '配合冷却器改造'),
    (3, 1,  'CZP-CD-03', CURDATE(),                           '1号主变 1011 隔离开关由运行转检修',            '黄志强', '刘玉兰', '张伟',   'PENDING',  '待执行，注意防跳步'),
    (4, 9,  'CZP-HX-01', CURDATE(),                           '35kV河西二线 321 断路器由运行转检修',           '孙振华', '周涛',   '赵刚',   'RUNNING',  '已执行前 3 步'),
    (5, 8,  'CZP-HX-02', CURDATE(),                           '35kV河西线由运行转冷备',                        '黄志强', '周涛',   '孙磊',   'PENDING',  '与工作票 GZP-HX-01 联动，许可前必须执行到位'),
    (6, 11, 'CZP-BL-01', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '1号主变由运行转检修',                          '孙振华', '吴斌',   '郑楠',   'PENDING',  '明日预安排'),
    (7, 6,  'CZF-CD-01', CURDATE(),                           '110kV母联间隔由检修转冷备（拆除安措）',          '黄志强', '刘玉兰', '张伟',   'PENDING',  '终结工作票 GZP-CD-02 前须完成对应恢复步骤'),
    (8, 2,  'CZF-CD-02', DATE_SUB(CURDATE(), INTERVAL 2 DAY), '2号主变由检修转运行（恢复安措）',               '孙振华', '刘玉兰', '陈国栋', 'FINISHED', '冷却器改造后恢复'),
    (9, 2,  'CZF-CD-03', CURDATE(),                           '2号主变检修安措恢复（滤油机作业结束）',          '孙振华', '刘玉兰', '王芳',   'FINISHED', '配合工作票 GZP-CD-03 终结'),
    (10, 4, 'CZF-CD-04', DATE_ADD(CURDATE(), INTERVAL 1 DAY), '35kV城东二线路安措拆除、恢复备用',              '黄志强', '周涛',   '郑楠',   'FINISHED', '预试结束恢复');

-- 9. 操作步骤 64 行（装设票对应工作票许可；恢复票对应工作票终结）
INSERT INTO `operation_step` (`ticket_id`, `step_no`, `action_desc`, `execute_person`, `execute_time`, `status`) VALUES
    -- 票1：母联转检修（已全部执行，对应今日 PERMITTED 的 GZP-CD-02 装设侧）
    (1, 1, '核对调度令：110kV II段母线转检修',                     '张伟', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:05:00'), 'DONE'),
    (1, 2, '检查 II段母线所带负荷已全部倒至 I 段母线',              '张伟', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:12:00'), 'DONE'),
    (1, 3, '拉开母联212断路器',                                    '张伟', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:20:00'), 'DONE'),
    (1, 4, '拉开母联2121、2122隔离开关',                           '张伟', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:28:00'), 'DONE'),
    (1, 5, '验明 II段母线确无电压',                                '张伟', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:35:00'), 'DONE'),
    (1, 6, '合上母联217接地刀闸',                                  '张伟', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:41:00'), 'DONE'),
    (1, 7, '在母联间隔四周装设临时遮栏',                            '张伟', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:46:00'), 'DONE'),
    (1, 8, '在212断路器操作把手上悬挂"禁止合闸，有人工作"标示牌',     '张伟', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:48:00'), 'DONE'),
    -- 票2：2号主变转检修（已全部执行，对应已终结的 GZP-CD-01 装设侧）
    (2, 1, '核对调度令：2号主变转检修',                            '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 08:35:00'), 'DONE'),
    (2, 2, '拉开 2 号主变低压侧 601 断路器',                        '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 08:44:00'), 'DONE'),
    (2, 3, '拉开 2 号主变高压侧 201 断路器',                        '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 08:52:00'), 'DONE'),
    (2, 4, '拉开 2011、2012 隔离开关',                             '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 09:00:00'), 'DONE'),
    (2, 5, '合上 2017 接地刀闸',                                   '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 09:09:00'), 'DONE'),
    (2, 6, '在2号主变10kV套管侧装设接地线一组',                      '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 09:14:00'), 'DONE'),
    (2, 7, '在主变本体及冷却器周围装设遮栏',                         '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 09:18:00'), 'DONE'),
    (2, 8, '在工作地点悬挂"在此工作"标示牌',                         '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 1 DAY), ' 09:20:00'), 'DONE'),
    -- 票3：1011隔离开关转检修（全部待执行）
    (3, 1, '核对调度令：1011 隔离开关转检修',                      NULL, NULL, 'PENDING'),
    (3, 2, '检查 1 号主变负荷已由 2 号主变带出',                   NULL, NULL, 'PENDING'),
    (3, 3, '拉开 101 断路器',                                      NULL, NULL, 'PENDING'),
    (3, 4, '拉开 1011 隔离开关',                                   NULL, NULL, 'PENDING'),
    (3, 5, '拉开 1012 隔离开关',                                   NULL, NULL, 'PENDING'),
    (3, 6, '验明 1011 隔离开关两侧确无电压',                        NULL, NULL, 'PENDING'),
    (3, 7, '合上 1017 接地刀闸，悬挂"禁止合闸"标示牌',              NULL, NULL, 'PENDING'),
    -- 票4：321断路器转检修（前 3 步已执行；对应 GZP-HX-02 装设侧，尚差接地与围栏）
    (4, 1, '核对调度令：321 断路器转检修',                         '赵刚', CONCAT(CURDATE(), ' 08:20:00'), 'DONE'),
    (4, 2, '拉开 321 断路器',                                      '赵刚', CONCAT(CURDATE(), ' 08:28:00'), 'DONE'),
    (4, 3, '拉开 3211、3212 隔离开关',                             '赵刚', CONCAT(CURDATE(), ' 08:36:00'), 'DONE'),
    (4, 4, '验明 321 断路器两侧确无电压',                           NULL, NULL, 'PENDING'),
    (4, 5, '合上 3217 接地刀闸',                                   NULL, NULL, 'PENDING'),
    (4, 6, '在机构箱操作把手上悬挂"禁止合闸"标示牌',                NULL, NULL, 'PENDING'),
    (4, 7, '在321断路器线路侧挂接地线一组',                          NULL, NULL, 'PENDING'),
    (4, 8, '在321断路器间隔四周装设遮栏',                            NULL, NULL, 'PENDING'),
    -- 票5：35kV河西线转冷备（全部待执行；对应已签发 GZP-HX-01 装设侧）
    (5, 1, '核对调度令：35kV河西线转冷备',                          NULL, NULL, 'PENDING'),
    (5, 2, '拉开 311 断路器',                                      NULL, NULL, 'PENDING'),
    (5, 3, '拉开 3111、3112 隔离开关',                             NULL, NULL, 'PENDING'),
    (5, 4, '验电后在311线路侧挂接地线一组',                          NULL, NULL, 'PENDING'),
    (5, 5, '合上 3117 线路侧接地刀闸',                              NULL, NULL, 'PENDING'),
    (5, 6, '在311断路器间隔装设遮栏',                               NULL, NULL, 'PENDING'),
    (5, 7, '在操作把手上悬挂"禁止合闸，有人工作"标示牌',              NULL, NULL, 'PENDING'),
    -- 票6：白龙1号主变转检修（明日，全部待执行；对应已签发 GZP-BL-01）
    (6, 1, '核对调度令：1号主变转检修',                             NULL, NULL, 'PENDING'),
    (6, 2, '拉开 101 断路器',                                      NULL, NULL, 'PENDING'),
    (6, 3, '拉开 1011、1012 隔离开关',                             NULL, NULL, 'PENDING'),
    (6, 4, '验明 101 断路器两侧确无电压',                            NULL, NULL, 'PENDING'),
    (6, 5, '合上 1017 接地刀闸',                                   NULL, NULL, 'PENDING'),
    (6, 6, '在1号主变本体侧挂接地线一组',                            NULL, NULL, 'PENDING'),
    (6, 7, '在主变间隔四周装设遮栏',                                 NULL, NULL, 'PENDING'),
    (6, 8, '在101操作把手上悬挂"禁止合闸，有人工作"标示牌',           NULL, NULL, 'PENDING'),
    -- 票7：母联检修转冷备恢复票（待执行；终结 GZP-CD-02 前必须完成 3/4/5 步）
    (7, 1, '核对调度令：110kV母联由检修转冷备',                      NULL, NULL, 'PENDING'),
    (7, 2, '检查母联间隔具备恢复条件、人员全部撤离',                   NULL, NULL, 'PENDING'),
    (7, 3, '拉开母联217接地刀闸',                                   NULL, NULL, 'PENDING'),
    (7, 4, '拆除母联间隔临时遮栏',                                   NULL, NULL, 'PENDING'),
    (7, 5, '取下"禁止合闸，有人工作"标示牌',                          NULL, NULL, 'PENDING'),
    -- 票8：2号主变检修转运行恢复票（已全部执行；对应 GZP-CD-01 终结侧）
    (8, 1, '核对调度令：2号主变由检修转运行',                        '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 17:05:00'), 'DONE'),
    (8, 2, '拉开 2017 接地刀闸',                                   '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 17:12:00'), 'DONE'),
    (8, 3, '拆除2号主变10kV套管侧接地线一组',                        '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 17:18:00'), 'DONE'),
    (8, 4, '拆除主变本体及冷却器周围遮栏',                           '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 17:22:00'), 'DONE'),
    (8, 5, '收回"在此工作"标示牌',                                  '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 17:24:00'), 'DONE'),
    -- 票9：2号主变滤油作业安措恢复票（已全部执行；对应 GZP-CD-03 终结侧）
    (9, 1, '核对作业结束、工作班人员撤离',                           '王芳', CONCAT(CURDATE(), ' 19:05:00'), 'DONE'),
    (9, 2, '拉开 2017 接地刀闸（验明确在分位）',                     '王芳', CONCAT(CURDATE(), ' 19:12:00'), 'DONE'),
    (9, 3, '拆除工作区域围栏',                                      '王芳', CONCAT(CURDATE(), ' 19:16:00'), 'DONE'),
    (9, 4, '收回"在此工作"标示牌',                                  '王芳', CONCAT(CURDATE(), ' 19:18:00'), 'DONE'),
    -- 票10：城东二线预试安措恢复票（已全部执行；对应 GZP-CD-05 终结侧）
    (10, 1, '核对预试结束、线路具备恢复条件',                        '郑楠', CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 15:05:00'), 'DONE'),
    (10, 2, '拆除线路侧临时接地线',                                  '郑楠', CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 15:14:00'), 'DONE'),
    (10, 3, '拆除围栏遮栏',                                         '郑楠', CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 15:20:00'), 'DONE'),
    (10, 4, '收回标示牌',                                           '郑楠', CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 15:24:00'), 'DONE');

-- 10. 工作票安全措施（逐项确认；状态与三审阶段一致）
--    装设侧关联 op_ticket_id/op_step_no（许可前核对），恢复侧关联 restore_*（终结前核对）。
INSERT INTO `safety_measure`
(`ticket_id`, `seq_no`, `measure_type`, `bay_id`, `location`, `required_action`, `signboard_text`,
 `op_ticket_id`, `op_step_no`, `restore_ticket_id`, `restore_step_no`,
 `status`, `execute_person`, `execute_time`, `remove_person`, `remove_time`, `change_remark`, `version`) VALUES
    -- GZP-CD-01（票1，已终结 -2d）：四类齐备，装设/拆除痕迹与两联操作票一致
    (1, 1, 'EARTH_SWITCH', 2, '2017 接地刀闸',                '合上接地刀闸',           NULL,                              2, 5, 8, 2, 'REMOVED', '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 08:58:00'), '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 17:12:00'), NULL, 2),
    (1, 2, 'GROUND_WIRE',  2, '2号主变10kV套管侧',             '装设接地线 1 组（编号 021）', NULL,                          2, 6, 8, 3, 'REMOVED', '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:01:00'), '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 17:18:00'), NULL, 2),
    (1, 3, 'BARRIER',      2, '主变本体及冷却器周围',          '装设封闭式遮栏',          NULL,                              2, 7, 8, 4, 'REMOVED', '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:04:00'), '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 17:22:00'), NULL, 2),
    (1, 4, 'SIGNBOARD',    2, '工作地点',                      '悬挂标示牌',              '在此工作',                         2, 8, 8, 5, 'REMOVED', '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 09:05:00'), '陈国栋', CONCAT(DATE_SUB(CURDATE(), INTERVAL 2 DAY), ' 17:24:00'), NULL, 2),
    -- GZP-HX-01（票2，已签发）：安措侧与操作票侧均未齐备 —— 许可时逐项指出卡点
    (2, 1, 'GROUND_WIRE',  8, '311断路器线路侧',               '装设接地线 1 组',         NULL,                              5, 4, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    (2, 2, 'EARTH_SWITCH', 8, '3117 线路侧接地刀闸',           '合上接地刀闸',            NULL,                              5, 5, NULL, NULL, 'EXECUTED', '孙磊', CONCAT(CURDATE(), ' 07:55:00'), NULL, NULL, NULL, 1),
    (2, 3, 'BARRIER',      8, '311断路器间隔',                 '装设遮栏',                NULL,                              5, 6, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    (2, 4, 'SIGNBOARD',    8, '311断路器操作把手',             '悬挂标示牌',              '禁止合闸，有人工作',               5, 7, NULL, NULL, 'EXECUTED', '孙磊', CONCAT(CURDATE(), ' 07:58:00'), NULL, NULL, NULL, 1),
    -- GZP-HX-02（票3，待签发）：措施已编排，关联票4后段步骤，均待执行
    (3, 1, 'EARTH_SWITCH', 9, '3217 接地刀闸',                 '合上接地刀闸',            NULL,                              4, 5, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    (3, 2, 'GROUND_WIRE',  9, '321断路器线路侧',               '装设接地线 1 组',         NULL,                              4, 7, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    (3, 3, 'BARRIER',      9, '321断路器间隔四周',             '装设遮栏',                NULL,                              4, 8, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    (3, 4, 'SIGNBOARD',    9, '机构箱操作把手',                '悬挂标示牌',              '禁止合闸，有人工作',               4, 6, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    -- GZP-CD-02（票4，已许可，今日进行中）：装设侧全部完成且票1对应步骤已执行；
    --   终结侧恢复票7尚待执行；另保留一条撤回许可后作废的旧措施痕迹
    (4, 1, 'EARTH_SWITCH', 6, '母联217接地刀闸',               '合上接地刀闸',            NULL,                              1, 6, 7, 3, 'PENDING_REMOVE', '张伟', CONCAT(CURDATE(), ' 08:58:00'), NULL, NULL, NULL, 1),
    (4, 2, 'BARRIER',      6, '母联间隔操作通道两侧',          '装设临时遮栏',            NULL,                              1, 7, 7, 4, 'PENDING_REMOVE', '张伟', CONCAT(CURDATE(), ' 09:01:00'), NULL, NULL, NULL, 1),
    (4, 3, 'SIGNBOARD',    6, '212断路器操作把手',             '悬挂标示牌',              '禁止合闸，有人工作',               1, 8, 7, 5, 'PENDING_REMOVE', '张伟', CONCAT(CURDATE(), ' 09:03:00'), NULL, NULL, NULL, 1),
    (4, 4, 'GROUND_WIRE',  6, '母联212断路器母线侧（旧桩位）',  '装设接地线 1 组',         NULL,                              NULL, NULL, NULL, NULL, 'CANCELED', '张伟', CONCAT(CURDATE(), ' 08:40:00'), NULL, NULL, '许可后现场核位发现桩头锈蚀，撤回许可后改挂至217-0接地点，本项作废', 2),
    -- GZP-CD-03（票5，已终结）：围栏与标示牌已拆除，恢复票9对应步骤已执行
    (5, 1, 'BARRIER',      2, '滤油机作业区域',                '装设安全围栏',            NULL,                              NULL, NULL, 9, 3, 'REMOVED', '王芳', CONCAT(CURDATE(), ' 13:50:00'), '王芳', CONCAT(CURDATE(), ' 19:16:00'), NULL, 2),
    (5, 2, 'SIGNBOARD',    2, '滤油机电源开关处',              '悬挂标示牌',              '在此工作',                         NULL, NULL, 9, 4, 'REMOVED', '王芳', CONCAT(CURDATE(), ' 13:52:00'), '王芳', CONCAT(CURDATE(), ' 19:18:00'), NULL, 2),
    -- GZP-CD-04（票6，待签发，不停电作业）：只设围栏与标示牌，无接地类措施
    (6, 1, 'BARRIER',      3, '311电流互感器作业点周围',       '装设绝缘围栏，保持安全距离不小于 0.7m', NULL,                 NULL, NULL, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    (6, 2, 'SIGNBOARD',    3, '作业区域入口',                  '悬挂标示牌',              '在此工作，有人工作',               NULL, NULL, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    -- GZP-BL-01（票7，已签发，明日）：四类措施与票6步骤均待执行，许可会被双侧拦住
    (7, 1, 'EARTH_SWITCH', 11, '1017 接地刀闸',                '合上接地刀闸',            NULL,                              6, 5, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    (7, 2, 'GROUND_WIRE',  11, '1号主变本体侧',                '装设接地线 1 组',         NULL,                              6, 6, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    (7, 3, 'BARRIER',      11, '主变间隔四周',                 '装设遮栏',                NULL,                              6, 7, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    (7, 4, 'SIGNBOARD',    11, '101断路器操作把手',            '悬挂标示牌',              '禁止合闸，有人工作',               6, 8, NULL, NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, 0),
    -- GZP-CD-05（票8，已终结 +1d）：接地线、遮栏、标示牌已拆除，恢复票10已执行
    (8, 1, 'GROUND_WIRE',  4, '35kV城东二线线路侧',            '装设接地线 1 组',         NULL,                              NULL, NULL, 10, 2, 'REMOVED', '郑楠', CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 09:55:00'), '郑楠', CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 15:14:00'), NULL, 2),
    (8, 2, 'BARRIER',      4, '避雷器及电缆头试验区域',        '装设遮栏',                NULL,                              NULL, NULL, 10, 3, 'REMOVED', '郑楠', CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 09:58:00'), '郑楠', CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 15:20:00'), NULL, 2),
    (8, 3, 'SIGNBOARD',    4, '线路侧隔离开关操作把手',        '悬挂标示牌',              '禁止合闸，有人工作',               NULL, NULL, 10, 4, 'REMOVED', '郑楠', CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 10:00:00'), '郑楠', CONCAT(DATE_ADD(CURDATE(), INTERVAL 1 DAY), ' 15:24:00'), NULL, 2);

-- 11. 工作票操作痕迹（仅为已许可的票4补一段撤回/变更/再许可的真实痕迹，演示刷新后仍可追溯）
INSERT INTO `work_ticket_log` (`ticket_id`, `action`, `measure_id`, `person`, `detail`, `created_at`) VALUES
    (4, 'ISSUE',  NULL, '朱明',   '工作票签发', CONCAT(CURDATE(), ' 08:10:00')),
    (4, 'MEASURE_EXECUTE', NULL, '张伟', '装设接地线 1 组于母联212断路器母线侧（旧桩位）', CONCAT(CURDATE(), ' 08:40:00')),
    (4, 'PERMIT', NULL, '刘玉兰', '许可开工', CONCAT(CURDATE(), ' 08:45:00')),
    (4, 'WITHDRAW', NULL, '刘玉兰', '现场核位发现桩头锈蚀，撤回许可后变更接地点', CONCAT(CURDATE(), ' 08:48:00')),
    (4, 'MEASURE_CANCEL', NULL, '张伟', '原「母联212断路器母线侧接地线」作废，改挂 217-0 接地点，原确认痕迹保留', CONCAT(CURDATE(), ' 08:50:00')),
    (4, 'MEASURE_ADD', NULL, '张伟', '新增/调整接地措施并重新执行装设确认', CONCAT(CURDATE(), ' 08:58:00')),
    (4, 'PERMIT', NULL, '刘玉兰', '变更项重新执行完毕、关联操作票步骤核对一致，再次许可', CONCAT(CURDATE(), ' 09:05:00'));
