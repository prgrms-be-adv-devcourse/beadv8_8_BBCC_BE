-- 리뷰 작성자가 탈퇴한 시각. 탈퇴하면 리뷰는 남기고 닉네임을 지우며 이 시각을 처음 한 번만 기록한다
ALTER TABLE community.review ADD COLUMN author_withdrawn_at timestamp(6) without time zone;
