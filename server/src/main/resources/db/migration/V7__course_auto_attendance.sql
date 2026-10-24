ALTER TABLE course_record ADD COLUMN automatic TINYINT NOT NULL DEFAULT 0 AFTER note;

CREATE TABLE course_auto_attendance_state (
    id TINYINT PRIMARY KEY,
    enabled_from DATETIME NOT NULL,
    scanned_through DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
