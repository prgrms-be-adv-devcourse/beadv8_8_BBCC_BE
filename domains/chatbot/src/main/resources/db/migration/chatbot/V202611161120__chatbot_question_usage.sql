-- 서비스 전체의 하루 챗봇 질문 수(#1388). 날짜(한국 시간)마다 한 줄이고 질문마다 1씩 올린다.
-- 질문마다 OpenAI를 여러 번 부르므로 하루 상한을 둔다. 챗봇 서버가 여러 대여도 같이 세도록 서버 메모리가 아니라 표에 둔다
CREATE TABLE chatbot.question_usage (
    usage_date date NOT NULL,
    question_count integer NOT NULL,
    CONSTRAINT question_usage_pkey PRIMARY KEY (usage_date),
    CONSTRAINT question_usage_count_check CHECK (question_count >= 0)
);
