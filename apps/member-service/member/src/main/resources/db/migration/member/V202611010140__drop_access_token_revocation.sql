-- Access Token 무효화는 Gateway가 member.tokens-revoked로 안다(#638). 회원은 이벤트만 발행하고 기록을 남기지 않는다
DROP TABLE IF EXISTS member.access_token_revocation;
