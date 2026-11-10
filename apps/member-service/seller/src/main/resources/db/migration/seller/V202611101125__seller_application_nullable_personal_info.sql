-- 신청한 회원이 탈퇴하면 신청의 개인정보(대표자명 · 사업자등록번호 · 신청자 닉네임)를 비우고 탈퇴 시각을 남긴다 (#1093)
ALTER TABLE seller.seller_application ALTER COLUMN representative_name DROP NOT NULL;
ALTER TABLE seller.seller_application ALTER COLUMN business_registration_number DROP NOT NULL;
ALTER TABLE seller.seller_application ALTER COLUMN applicant_nickname DROP NOT NULL;
ALTER TABLE seller.seller_application ADD COLUMN applicant_withdrawn_at timestamp(6) without time zone;
