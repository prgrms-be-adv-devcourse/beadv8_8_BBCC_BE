-- 알림함 부하 측정 데이터 지우기. seed.sql이 넣은 받는 사람 범위(회원 9500001~9510000, 9600001)만 지운다. 다시 돌려도 안전하다
BEGIN;

DELETE FROM notification.inbox
WHERE recipient_type = 'MEMBER'
  AND (recipient_id BETWEEN 9500001 AND 9510000 OR recipient_id = 9600001);

COMMIT;

VACUUM ANALYZE notification.inbox;
