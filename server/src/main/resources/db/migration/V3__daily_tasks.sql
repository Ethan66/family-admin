CREATE TABLE daily_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    member_name VARCHAR(100) NOT NULL,
    title VARCHAR(120) NOT NULL,
    task_date DATE NOT NULL,
    task_time TIME NULL,
    category VARCHAR(40) NOT NULL DEFAULT '',
    repeat_daily TINYINT NOT NULL DEFAULT 0,
    note VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_task_family_member_date (family_id, member_id, task_date, deleted),
    CONSTRAINT fk_task_family FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT fk_task_member FOREIGN KEY (member_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE daily_task_completion (
    task_id BIGINT NOT NULL,
    task_date DATE NOT NULL,
    family_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id, task_date),
    INDEX idx_completion_family_date (family_id, task_date),
    CONSTRAINT fk_completion_task FOREIGN KEY (task_id) REFERENCES daily_task(id),
    CONSTRAINT fk_completion_family FOREIGN KEY (family_id) REFERENCES family(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
