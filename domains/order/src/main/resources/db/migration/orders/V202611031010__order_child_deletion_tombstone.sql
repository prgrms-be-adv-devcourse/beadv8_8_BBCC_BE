-- #739 프로필을 아직 모르는 자녀도 삭제 사실과 시각을 남긴다.
-- 기존 정상 행의 키 · 몸무게 필수 규칙은 CHECK로 유지한다.
ALTER TABLE orders.replica_child
    ALTER COLUMN height_cm DROP NOT NULL,
    ALTER COLUMN weight_kg DROP NOT NULL,
    ADD CONSTRAINT replica_child_active_profile_check
        CHECK (is_deleted OR (height_cm IS NOT NULL AND weight_kg IS NOT NULL));
