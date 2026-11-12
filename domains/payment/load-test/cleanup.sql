-- seed.sql이 넣은 범위만 지운다. 다시 돌려도 안전하다.
DELETE FROM payment.refund_item WHERE refund_id BETWEEN 90000001 AND 90020000;
DELETE FROM payment.refund WHERE id BETWEEN 90000001 AND 90020000;
DELETE FROM payment.payment_item WHERE payment_id BETWEEN 90000001 AND 90020000;
DELETE FROM payment.payment WHERE id BETWEEN 90000001 AND 90020000;
