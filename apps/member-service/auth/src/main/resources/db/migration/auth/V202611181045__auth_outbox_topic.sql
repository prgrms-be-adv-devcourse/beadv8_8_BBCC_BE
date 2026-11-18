-- 인증 아웃박스도 회원 · 판매자 · 자녀 프로필 아웃박스처럼 보낼 토픽을 행에 남긴다. 네 표의 모양이 같아야 공통 엔티티 하나로 매핑된다.
-- 인증은 kidly.auth.events로만 보내므로 기존 행은 그 값으로 채운다.
-- 기본값은 남긴다. 이전 버전으로 되돌려도 topic을 모르는 코드의 저장이 실패하지 않게 하기 위해서다.
ALTER TABLE auth.auth_outbox ADD COLUMN topic character varying(100) NOT NULL DEFAULT 'kidly.auth.events';
