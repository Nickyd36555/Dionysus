-- Dionysus admin/multi-user schema (MySQL / MariaDB).
-- Applied automatically by install.php; kept here for reference and manual setup.

CREATE TABLE IF NOT EXISTS admins (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS users (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL UNIQUE,
    pin_hash     VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL DEFAULT '',
    status       ENUM('active','disabled') NOT NULL DEFAULT 'active',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS profiles (
    user_id          INT PRIMARY KEY,
    xtream_host      VARCHAR(255) NOT NULL DEFAULT '',
    xtream_user      VARCHAR(128) NOT NULL DEFAULT '',
    xtream_pass      VARCHAR(128) NOT NULL DEFAULT '',
    epg_url          VARCHAR(512) NOT NULL DEFAULT '',
    debrid_token     VARCHAR(255) NOT NULL DEFAULT '',
    default_category VARCHAR(128) NOT NULL DEFAULT '',
    feature_vod       TINYINT(1)  NOT NULL DEFAULT 1,
    feature_downloads TINYINT(1)  NOT NULL DEFAULT 1,
    feature_search    TINYINT(1)  NOT NULL DEFAULT 1,
    CONSTRAINT fk_profiles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS devices (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT NOT NULL,
    device_id    VARCHAR(128) NOT NULL,
    device_name  VARCHAR(128) NOT NULL DEFAULT '',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uniq_user_device (user_id, device_id),
    CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tokens (
    token        CHAR(64) PRIMARY KEY,
    user_id      INT NOT NULL,
    device_id    VARCHAR(128) NOT NULL,
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
