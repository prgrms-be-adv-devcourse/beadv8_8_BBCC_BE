-- 알림이 받는 사람 이메일을 찾는 회원 이메일 복사본(#850). member.registered로 채우고 member.withdrawn으로 지운다
-- 이메일은 가입 뒤 바꿀 수 없어 갱신하지 않는다
CREATE TABLE notification.member_contact (
    member_id bigint NOT NULL,
    email character varying(320) NOT NULL,
    created_at timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT member_contact_pkey PRIMARY KEY (member_id)
);
