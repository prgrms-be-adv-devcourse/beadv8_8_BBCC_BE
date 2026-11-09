-- 탈퇴한 회원 표시(회원당 1행, 영구 보관). member.withdrawn을 처리할 때 남긴다. 회원 ID와 시각만 두고 개인정보는 두지 않는다
-- 탈퇴 뒤 늦게 온 · 관리자가 DLT에서 재처리한 member.registered가 이메일 복사본을 되살리거나 환영 메일을 보내지 않게 한다
-- 탈퇴 한 방향으로만 바뀐다. 같은 탈퇴를 다시 받아도 처음 시각을 그대로 둔다
CREATE TABLE notification.withdrawn_member (
    member_id bigint NOT NULL,
    withdrawn_at timestamp(6) without time zone NOT NULL,
    CONSTRAINT withdrawn_member_pkey PRIMARY KEY (member_id)
);
