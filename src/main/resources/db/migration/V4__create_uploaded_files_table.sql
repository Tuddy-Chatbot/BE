-- 채팅에 사용될 업로드 파일 정보 테이블
CREATE TABLE uploaded_files (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT       NOT NULL,
    original_filename   VARCHAR(255) NOT NULL,
    s3_key              VARCHAR(512) NOT NULL UNIQUE,
    created_at          DATETIME     NOT NULL,
    CONSTRAINT fk_uploaded_file_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 채팅 세션이 어떤 파일을 참조하는지 연결
ALTER TABLE chat_sessions
ADD COLUMN uploaded_file_id BIGINT NULL AFTER user_id,
ADD CONSTRAINT fk_session_uploaded_file FOREIGN KEY (uploaded_file_id) REFERENCES uploaded_files (id) ON DELETE SET NULL;