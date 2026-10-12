UPDATE sys_user
SET password_hash = '$2a$10$iD6G94Q2todnn5zY66WkOO2bSV8twRG5jp658DoSaTXAkvyZFLwXW',
    token_version = token_version + 1,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'admin'
  AND super_admin = 1
  AND deleted = 0;
