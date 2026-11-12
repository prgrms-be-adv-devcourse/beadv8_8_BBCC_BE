-- #1244 자녀 성장 기록 이벤트를 받아 저장하는 표를 만든다. 다른 도메인 데이터의 복사본이라 원래 기록 ID를 PK로 쓴다.
-- 자녀는 다른 도메인의 ID라 FK 없이 값으로만 둔다. 체형은 입력하지 않을 수 있어 NULL을 허용한다.
-- 자녀 ID로 기록을 지우거나 읽으므로 child_profile_id에 색인을 만든다.
CREATE TABLE recommend.growth_record (
    growth_record_id bigint NOT NULL,
    child_profile_id bigint NOT NULL,
    height_cm numeric(4,1) NOT NULL,
    body_type character varying(255),
    recorded_at timestamp(6) without time zone NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT growth_record_pkey PRIMARY KEY (growth_record_id),
    CONSTRAINT growth_record_body_type_check CHECK (((body_type)::text = ANY ((ARRAY['SLIM'::character varying, 'NORMAL'::character varying, 'CHUNKY'::character varying])::text[])))
);

CREATE INDEX idx_growth_record_child_profile_id ON recommend.growth_record (child_profile_id);
