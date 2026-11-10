-- #1125 AI 답을 24시간 동안 다시 쓰고, 서비스 전체의 하루 AI 호출 횟수에 상한을 두기 위한 표 두 개를 만든다.
-- 추천 서버가 여러 대여도 같은 DB에서 함께 보도록 서버 메모리가 아니라 표에 둔다.

-- AI에 보낸 입력으로 만든 키와 AI가 돌려준 답을 줄마다 남긴다. 입력 · 출력 로그 역할도 하므로
-- 같은 키라도 덮어쓰지 않고 줄을 새로 넣는다. 그래서 UNIQUE를 걸지 않고 updated_at을 두지 않는다.
-- 읽을 때는 종류와 키로 찾아 가장 최근 줄을 쓰므로 그 순서로 색인을 둔다.
CREATE TABLE recommend.ai_response (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    response_type varchar(30) NOT NULL,
    input_hash char(64) NOT NULL,
    response jsonb NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT ai_response_type_check
        CHECK (response_type IN ('HOME_PREVIEW', 'BOUGHT_TOGETHER', 'SIZE_RECOMMENDATION'))
);

CREATE INDEX ai_response_type_hash_created_idx
    ON recommend.ai_response (response_type, input_hash, created_at DESC);

-- 서비스 전체의 하루 AI 호출 횟수다. 날짜(한국 시간)마다 한 줄이고 호출할 때마다 1씩 올린다.
CREATE TABLE recommend.ai_call_usage (
    usage_date date PRIMARY KEY,
    call_count integer NOT NULL CHECK (call_count >= 0)
);
