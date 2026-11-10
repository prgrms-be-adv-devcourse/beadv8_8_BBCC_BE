-- #939 자녀 · 회원별 카테고리 · 브랜드 친밀도와 취향 벡터를 저장하는 표를 만든다.
-- 매일 배치가 대상마다 이 표의 줄을 지우고 새로 넣는 파생 표라서 대리키와 생성 · 수정 시각을 두지 않는다.
-- 자녀 ID와 회원 ID는 FK 없이 값으로만 참조한다.
CREATE TABLE recommend.child_category_affinity (
    child_profile_id bigint NOT NULL,
    category_id bigint NOT NULL,
    score double precision NOT NULL,
    calculated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT child_category_affinity_pkey PRIMARY KEY (child_profile_id, category_id),
    CONSTRAINT child_category_affinity_score_check CHECK (score >= 0 AND score <= 1)
);

CREATE TABLE recommend.child_brand_affinity (
    child_profile_id bigint NOT NULL,
    brand text NOT NULL,
    score double precision NOT NULL,
    calculated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT child_brand_affinity_pkey PRIMARY KEY (child_profile_id, brand),
    CONSTRAINT child_brand_affinity_score_check CHECK (score >= 0 AND score <= 1)
);

CREATE TABLE recommend.member_category_affinity (
    member_id bigint NOT NULL,
    category_id bigint NOT NULL,
    score double precision NOT NULL,
    calculated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT member_category_affinity_pkey PRIMARY KEY (member_id, category_id),
    CONSTRAINT member_category_affinity_score_check CHECK (score >= 0 AND score <= 1)
);

CREATE TABLE recommend.member_brand_affinity (
    member_id bigint NOT NULL,
    brand text NOT NULL,
    score double precision NOT NULL,
    calculated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT member_brand_affinity_pkey PRIMARY KEY (member_id, brand),
    CONSTRAINT member_brand_affinity_score_check CHECK (score >= 0 AND score <= 1)
);

-- 본 · 담은 상품의 임베딩을 무게로 합쳐 길이 1로 맞춘 벡터다.
-- weight_sum은 요청 때 자녀 몫과 회원 몫을 합칠 때 무게로 쓴다. activity_count는 합친 활동 수다.
CREATE TABLE recommend.child_taste_vector (
    child_profile_id bigint NOT NULL,
    embedding recommend.vector(1536) NOT NULL,
    weight_sum double precision NOT NULL,
    activity_count integer NOT NULL,
    calculated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT child_taste_vector_pkey PRIMARY KEY (child_profile_id)
);

CREATE TABLE recommend.member_taste_vector (
    member_id bigint NOT NULL,
    embedding recommend.vector(1536) NOT NULL,
    weight_sum double precision NOT NULL,
    activity_count integer NOT NULL,
    calculated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT member_taste_vector_pkey PRIMARY KEY (member_id)
);

-- 자녀를 고르지 않은 회원 활동의 기간 조회와 대상 회원 찾기에 쓴다.
CREATE INDEX idx_activity_record_member_id_occurred_at_member_only
    ON recommend.activity_record USING btree (member_id, occurred_at) WHERE child_profile_id IS NULL;

-- 자녀별 구매 기간 조회와 대상 자녀 찾기에 쓴다.
CREATE INDEX idx_purchase_record_child_profile_id_confirmed_at
    ON recommend.purchase_record USING btree (child_profile_id, confirmed_at);
