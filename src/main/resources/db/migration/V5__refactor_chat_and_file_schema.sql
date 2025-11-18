-- 1. UploadedFile에 처리 상태 컬럼 추가 (기본값: PENDING)
ALTER TABLE uploaded_files
ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

-- 2. ChatSession에서 파일 참조 제거 (세션 단위 -> 메시지 단위로 변경)
ALTER TABLE chat_sessions
DROP FOREIGN KEY fk_session_uploaded_file;

ALTER TABLE chat_sessions
DROP COLUMN uploaded_file_id;

-- 3. ChatMessage에 파일 참조 추가
ALTER TABLE chat_messages
ADD COLUMN uploaded_file_id BIGINT NULL;

ALTER TABLE chat_messages
ADD CONSTRAINT fk_message_uploaded_file
FOREIGN KEY (uploaded_file_id) REFERENCES uploaded_files (id) ON DELETE SET NULL;