-- 보존 기간(1년)이 지난 발송 기록 정리
CREATE INDEX idx_notification_created_at ON notification.notification (created_at);
