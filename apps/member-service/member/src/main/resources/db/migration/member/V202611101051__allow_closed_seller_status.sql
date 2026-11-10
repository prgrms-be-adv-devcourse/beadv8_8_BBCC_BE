-- 판매자 상태 복사본이 탈퇴한 판매자(CLOSED)를 받을 수 있게 한다 (#1092)
ALTER TABLE member.member DROP CONSTRAINT member_seller_status_check;
ALTER TABLE member.member ADD CONSTRAINT member_seller_status_check
    CHECK (seller_status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'));
