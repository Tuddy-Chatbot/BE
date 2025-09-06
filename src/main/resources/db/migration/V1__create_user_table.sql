-- 사용자 테이블 생성
CREATE TABLE user_accounts (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  login_id         VARCHAR(50)  NULL,
  email            VARCHAR(320) NULL,
  display_name     VARCHAR(60)  NOT NULL,
  password_hash    VARCHAR(100) NULL,
  provider         VARCHAR(20)  NOT NULL,          -- LOCAL, GOOGLE, NAVER, KAKAO
  provider_user_id VARCHAR(100) NULL,
  role             VARCHAR(20)  NOT NULL DEFAULT 'USER',
  status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
  last_login_at    DATETIME     NULL,
  created_at       DATETIME     NOT NULL,
  updated_at       DATETIME     NOT NULL,
  CONSTRAINT uk_user_login_id UNIQUE (login_id),
  CONSTRAINT uk_user_email UNIQUE (email),
  CONSTRAINT uk_user_provider_uid UNIQUE (provider, provider_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_user_email ON user_accounts(email);
CREATE INDEX idx_user_provider ON user_accounts(provider, provider_user_id);
