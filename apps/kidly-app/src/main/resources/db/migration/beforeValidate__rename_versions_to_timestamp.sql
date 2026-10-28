-- 마이그레이션 버전을 순번(1~6)에서 파일을 만든 시각(yyyyMMddHHmm)으로 바꾼 뒤(#393), 이미 적용된 DB의 이력을 맞춘다.
-- Flyway가 검증하기 전에 매번 실행된다. 이력 테이블이 없거나(새 DB) 이미 바뀌었으면 아무것도 하지 않는다.
-- 파일 내용 · 설명은 그대로라 checksum 검증은 바뀌지 않는다. baseline 행(dev 서버의 버전 1)도 같이 바꾼다
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        UPDATE public.flyway_schema_history AS h
        SET version = m.new_version
        FROM (VALUES ('1', '202610160256'),
                     ('2', '202610160855'),
                     ('3', '202610161035'),
                     ('4', '202610161145'),
                     ('5', '202610161229'),
                     ('6', '202610161458')) AS m (old_version, new_version)
        WHERE h.version = m.old_version;
    END IF;
END
$$;
