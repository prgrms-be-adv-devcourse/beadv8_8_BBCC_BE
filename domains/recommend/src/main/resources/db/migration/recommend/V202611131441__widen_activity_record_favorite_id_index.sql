-- #1316 찜 ID 색인을 해제 줄에서 찜 ID가 있는 줄 전체로 넓힌다.
-- 탈퇴로 지운 찜은 그 찜의 찜 · 해제 기록을 찜 ID로 함께 지우는데, 해제 줄만 담은 색인으로는 찜 줄을 찾지 못해 표 전체를 훑는다.
-- 선호 집계가 같은 찜의 해제 기록을 찾을 때도 이 색인을 쓴다. 찜이 아닌 기록은 찜 ID가 비어 있어 담지 않는다.
DROP INDEX recommend.idx_activity_record_favorite_removed_favorite_id;

CREATE INDEX idx_activity_record_favorite_id ON recommend.activity_record (favorite_id)
    WHERE favorite_id IS NOT NULL;
