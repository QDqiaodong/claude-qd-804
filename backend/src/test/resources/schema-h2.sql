-- 测试专用 H2（MySQL 兼容模式）结构，字段与 mysql/schema.sql 对齐；不含存储过程。
DROP TABLE IF EXISTS work_ticket_log;
DROP TABLE IF EXISTS safety_measure;
DROP TABLE IF EXISTS operation_step;
DROP TABLE IF EXISTS operation_ticket;
DROP TABLE IF EXISTS work_ticket;
DROP TABLE IF EXISTS defect;
DROP TABLE IF EXISTS equipment;
DROP TABLE IF EXISTS bay;
DROP TABLE IF EXISTS substation;

CREATE TABLE substation (
    id BIGINT PRIMARY KEY,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    area VARCHAR(64),
    dispatch_org VARCHAR(64),
    watch_mode VARCHAR(32),
    commission_date DATE,
    remark VARCHAR(255)
);

CREATE TABLE bay (
    id BIGINT PRIMARY KEY,
    substation_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    bay_type VARCHAR(32) NOT NULL,
    voltage_level VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    sort_no INT,
    remark VARCHAR(255)
);

CREATE TABLE equipment (
    id BIGINT PRIMARY KEY,
    bay_id BIGINT NOT NULL,
    equipment_no VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    equip_type VARCHAR(32) NOT NULL,
    model VARCHAR(64),
    vendor VARCHAR(64),
    commission_date DATE,
    last_check_date DATE,
    status VARCHAR(24) NOT NULL,
    remark VARCHAR(255)
);

CREATE TABLE defect (
    id BIGINT PRIMARY KEY,
    bay_id BIGINT NOT NULL,
    equipment_id BIGINT,
    defect_no VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    level VARCHAR(16) NOT NULL,
    found_time DATETIME NOT NULL,
    reporter VARCHAR(32),
    due_date DATE NOT NULL,
    owner VARCHAR(32),
    status VARCHAR(24) NOT NULL,
    closed_time DATETIME
);

CREATE TABLE work_ticket (
    id BIGINT PRIMARY KEY,
    bay_id BIGINT NOT NULL,
    ticket_no VARCHAR(64),
    issue_date DATE NOT NULL,
    work_leader VARCHAR(32),
    team_members VARCHAR(255),
    work_content VARCHAR(512),
    power_cut_range VARCHAR(512),
    safety_measures VARCHAR(512),
    plan_start_time DATETIME,
    plan_end_time DATETIME,
    issuer VARCHAR(32),
    permittor VARCHAR(32),
    terminator VARCHAR(32),
    issue_time DATETIME,
    permit_time DATETIME,
    terminate_time DATETIME,
    status VARCHAR(24) NOT NULL,
    remark VARCHAR(255)
);

CREATE TABLE operation_ticket (
    id BIGINT PRIMARY KEY,
    bay_id BIGINT NOT NULL,
    ticket_no VARCHAR(64),
    issue_date DATE NOT NULL,
    task_name VARCHAR(255) NOT NULL,
    dispatcher VARCHAR(32),
    guardian VARCHAR(32),
    operator_name VARCHAR(32),
    status VARCHAR(24) NOT NULL,
    remark VARCHAR(255)
);

CREATE TABLE operation_step (
    id BIGINT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    step_no INT NOT NULL,
    action_desc VARCHAR(255) NOT NULL,
    execute_person VARCHAR(32),
    execute_time DATETIME,
    status VARCHAR(24) NOT NULL
);

CREATE TABLE safety_measure (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    seq_no INT NOT NULL,
    measure_type VARCHAR(16) NOT NULL,
    bay_id BIGINT NOT NULL,
    location VARCHAR(128) NOT NULL,
    required_action VARCHAR(255) NOT NULL,
    signboard_text VARCHAR(64),
    op_ticket_id BIGINT,
    op_step_no INT,
    restore_ticket_id BIGINT,
    restore_step_no INT,
    status VARCHAR(24) NOT NULL,
    execute_person VARCHAR(32),
    execute_time DATETIME,
    remove_person VARCHAR(32),
    remove_time DATETIME,
    change_remark VARCHAR(255),
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE work_ticket_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    measure_id BIGINT,
    person VARCHAR(32),
    detail VARCHAR(512),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
