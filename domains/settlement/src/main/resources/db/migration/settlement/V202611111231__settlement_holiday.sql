-- 영업일 판단에 쓰는 공휴일. API에서 사라진 행도 삭제하지 않는다.
CREATE TABLE settlement.settlement_holiday (
    holiday_on DATE PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITHOUT TIME ZONE NOT NULL
);
