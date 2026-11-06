-- 확장은 슈퍼유저만 켤 수 있다. 서비스 연결의 search_path에 recommend가 없어서 SQL은 recommend.vector처럼 스키마를 붙여 쓴다
CREATE EXTENSION IF NOT EXISTS vector SCHEMA recommend;

-- 다른 스키마에 이미 있으면 위 문장이 넘어가 recommend.vector가 없으므로, 벡터 칸을 만들기 전에 멈춘다
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_extension e
        JOIN pg_namespace n ON n.oid = e.extnamespace
        WHERE e.extname = 'vector' AND n.nspname = 'recommend'
    ) THEN
        RAISE EXCEPTION 'vector 확장이 recommend 스키마에 없습니다. recommend 스키마로 옮기거나 다시 만든 뒤 적용하세요';
    END IF;
END
$$;

-- 상품의 이름 · 브랜드 · 설명 · 정보 값 글(embedding_text)을 임베딩한 벡터. 아직 만들지 못했으면 null이다
ALTER TABLE recommend.replica_product ADD COLUMN embedding recommend.vector(1536);

-- embedding을 만든 글의 md5. md5(embedding_text)와 다르면 벡터가 낡았다
ALTER TABLE recommend.replica_product ADD COLUMN embedded_text_md5 character(32);

-- 임베딩 API에 보냈지만 아직 성공하지 못한 시각. 성공하면 null로 돌린다
ALTER TABLE recommend.replica_product ADD COLUMN embedding_attempted_at timestamp(6) without time zone;
