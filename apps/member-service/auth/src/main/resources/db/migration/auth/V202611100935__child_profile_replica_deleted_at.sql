-- 자녀 삭제는 되돌릴 수 없으므로 행을 지우지 않고 표시를 남겨 늦게 온 child.changed가 복사본을 되살리지 못하게 한다
ALTER TABLE auth.child_profile_replica ADD COLUMN deleted_at timestamp(6);
