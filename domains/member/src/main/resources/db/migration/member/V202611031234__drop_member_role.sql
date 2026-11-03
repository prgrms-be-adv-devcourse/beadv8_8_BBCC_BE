-- 관리자 계정은 회원과 별도 주체다.
ALTER TABLE member.member DROP COLUMN role;
