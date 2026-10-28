-- FE 시연 역할 전환 설정. 관리자 화면에서 바꾼다 (#426)
-- 기본값: 켬 (FE 빌드 값의 기본값과 같다). 기존 행도 켬으로 채운다
ALTER TABLE setting.app_setting
    ADD COLUMN is_demo_role_switch_enabled boolean NOT NULL DEFAULT true;
