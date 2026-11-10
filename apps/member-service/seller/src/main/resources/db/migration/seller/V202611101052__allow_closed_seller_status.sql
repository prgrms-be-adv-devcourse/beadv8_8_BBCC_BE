-- 회원 탈퇴로 닫힌 판매자(CLOSED)를 저장할 수 있게 한다 (#1092)
ALTER TABLE seller.seller DROP CONSTRAINT seller_status_check;
ALTER TABLE seller.seller ADD CONSTRAINT seller_status_check
    CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED'));
