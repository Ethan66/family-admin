CREATE TABLE family (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remark VARCHAR(500) NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_family_status_deleted (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    super_admin TINYINT NOT NULL DEFAULT 0,
    token_version INT NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_user_username (username),
    INDEX idx_user_status_deleted (status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE family_member (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_member_user (user_id, deleted),
    INDEX idx_member_family_role (family_id, role, deleted),
    CONSTRAINT fk_member_family FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT fk_member_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE transaction_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    amount DECIMAL(14,2) NOT NULL,
    transaction_date DATE NOT NULL,
    category VARCHAR(50) NOT NULL,
    member_name VARCHAR(100) NOT NULL,
    note VARCHAR(500) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_tx_family_date (family_id, transaction_date, deleted),
    INDEX idx_tx_family_type_category (family_id, type, category, deleted),
    CONSTRAINT fk_tx_family FOREIGN KEY (family_id) REFERENCES family(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exam_paper (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    subject VARCHAR(30) NOT NULL,
    grade VARCHAR(30) NOT NULL,
    exam_date DATE NOT NULL,
    full_score DECIMAL(6,2) NOT NULL,
    score DECIMAL(6,2) NULL,
    status VARCHAR(20) NOT NULL,
    review_note VARCHAR(1000) NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_exam_family_date (family_id, exam_date, deleted),
    INDEX idx_exam_family_filters (family_id, subject, grade, status, deleted),
    CONSTRAINT fk_exam_family FOREIGN KEY (family_id) REFERENCES family(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE file_object (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(255) NOT NULL,
    media_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_file_storage_key (storage_key),
    INDEX idx_file_family_status (family_id, status, deleted),
    CONSTRAINT fk_file_family FOREIGN KEY (family_id) REFERENCES family(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE photo (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    album VARCHAR(50) NOT NULL,
    photo_date DATE NOT NULL,
    description VARCHAR(1000) NULL,
    file_id BIGINT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_photo_family_date (family_id, photo_date, deleted),
    INDEX idx_photo_family_album (family_id, album, deleted),
    CONSTRAINT fk_photo_family FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT fk_photo_file FOREIGN KEY (file_id) REFERENCES file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE movie (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    type VARCHAR(30) NOT NULL,
    release_year SMALLINT NOT NULL,
    director VARCHAR(100) NULL,
    minutes INT NOT NULL,
    rating DECIMAL(3,1) NULL,
    watch_status VARCHAR(20) NOT NULL,
    progress INT NOT NULL DEFAULT 0,
    description VARCHAR(2000) NULL,
    cover_file_id BIGINT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_movie_family_filters (family_id, type, watch_status, deleted),
    CONSTRAINT fk_movie_family FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT fk_movie_cover FOREIGN KEY (cover_file_id) REFERENCES file_object(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE study_note (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    family_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    tag VARCHAR(50) NOT NULL,
    markdown_body MEDIUMTEXT NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    INDEX idx_note_family_tag_updated (family_id, tag, updated_at, deleted),
    CONSTRAINT fk_note_family FOREIGN KEY (family_id) REFERENCES family(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    family_id BIGINT NULL,
    operator_id BIGINT NOT NULL,
    action VARCHAR(80) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(80) NULL,
    result VARCHAR(20) NOT NULL,
    context_json JSON NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_log_family_created (family_id, created_at),
    INDEX idx_log_operator_created (operator_id, created_at),
    CONSTRAINT fk_log_family FOREIGN KEY (family_id) REFERENCES family(id),
    CONSTRAINT fk_log_operator FOREIGN KEY (operator_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

