-- 채팅 세션 테이블 생성
CREATE TABLE chat_sessions (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id      BIGINT       NOT NULL,
  title        VARCHAR(255) NOT NULL,
  created_at   DATETIME     NOT NULL,
  CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 채팅 메시지 테이블 생성
CREATE TABLE chat_messages (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id   BIGINT        NOT NULL,
  sender_type  VARCHAR(20)   NOT NULL, -- 'USER', 'BOT'
  content      TEXT          NOT NULL,
  created_at   DATETIME      NOT NULL,
  CONSTRAINT fk_message_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_chat_session_user_id ON chat_sessions(user_id);
CREATE INDEX idx_chat_message_session_id ON chat_messages(session_id);