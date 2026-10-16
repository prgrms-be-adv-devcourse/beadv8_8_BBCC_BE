-- FE 동작 설정(목 데이터 · 시연 역할 전환). 관리자 화면에서 바꾼다 (#378)
CREATE SCHEMA IF NOT EXISTS setting;

CREATE TABLE setting.app_setting (
    is_demo_role_switch_enabled boolean NOT NULL,
    is_mock_enabled boolean NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT app_setting_pkey PRIMARY KEY (id)
);

-- 기본값: 둘 다 켬 (FE 빌드 값의 기본값과 같다)
INSERT INTO setting.app_setting (is_demo_role_switch_enabled, is_mock_enabled, created_at, id, updated_at)
VALUES (true, true, LOCALTIMESTAMP, 1, LOCALTIMESTAMP);
