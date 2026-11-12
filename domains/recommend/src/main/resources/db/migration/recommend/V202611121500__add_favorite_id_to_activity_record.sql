-- #1316 찜과 찜 해제 활동 기록에 찜 ID를 남긴다.
-- 선호 집계가 같은 찜의 해제 기록을 찾아 그 찜의 몫을 빼야 하기 때문이다.
-- 찜은 커뮤니티 도메인의 것이므로 FK 없이 값으로만 참조한다.
ALTER TABLE recommend.activity_record ADD COLUMN favorite_id BIGINT;

-- 해제 기록을 찜 ID로 찾는 데 쓴다. 찾는 곳은 해제 기록뿐이라 해제 줄만 담는 부분 색인으로 둔다.
CREATE INDEX idx_activity_record_favorite_removed_favorite_id ON recommend.activity_record (favorite_id)
    WHERE type = 'FAVORITE_REMOVED';
