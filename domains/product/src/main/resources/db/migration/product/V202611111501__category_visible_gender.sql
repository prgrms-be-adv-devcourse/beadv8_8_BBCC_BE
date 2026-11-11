-- kidly-app이 상품 마이그레이션을 이미 적용한 DB(통합 앱 시절의 dev · 공유 로컬 DB)에서도 상품 서비스 Flyway가 이 파일을 다시 돌린다.
-- 서비스 baseline이 이 버전보다 앞서기 때문이다. 다시 돌아도 실패하지 않고 이미 있는 행을 중복해서 넣지 않게 한다.
ALTER TABLE product.category ADD COLUMN IF NOT EXISTS visible_gender character varying(10) NOT NULL DEFAULT 'ALL';
ALTER TABLE product.category DROP CONSTRAINT IF EXISTS category_visible_gender_check;
ALTER TABLE product.category ADD CONSTRAINT category_visible_gender_check CHECK (visible_gender IN ('ALL', 'GIRL'));

-- 원피스는 여아 자녀에게만 보여 준다
UPDATE product.category SET visible_gender = 'GIRL'
WHERE name = '원피스' AND deleted_at IS NULL AND visible_gender <> 'GIRL'
  AND parent_id IN (SELECT id FROM product.category WHERE name = '의류' AND parent_id IS NULL AND deleted_at IS NULL);

-- 치마를 하의와 구분하려고 하의의 이름을 바지로 바꾼다. 같은 의류 아래에 바지가 이미 있으면 바꾸지 않는다
UPDATE product.category c SET name = '바지', updated_at = now()
WHERE c.name = '하의' AND c.deleted_at IS NULL
  AND c.parent_id IN (SELECT id FROM product.category WHERE name = '의류' AND parent_id IS NULL AND deleted_at IS NULL)
  AND NOT EXISTS (
      SELECT 1 FROM product.category x
      WHERE x.parent_id = c.parent_id AND x.name = '바지' AND x.deleted_at IS NULL);

-- 의류 아래에 치마가 없으면 바지와 같은 상품 유형으로 넣는다. 의류나 바지가 없는 빈 DB에는 아무 행도 넣지 않는다
INSERT INTO product.category (name, parent_id, product_type_id, visible_gender, created_at, updated_at)
SELECT '치마', p.id, b.product_type_id, 'GIRL', now(), now()
FROM product.category p
JOIN LATERAL (
    SELECT x.product_type_id FROM product.category x
    WHERE x.parent_id = p.id AND x.name = '바지' AND x.deleted_at IS NULL AND x.product_type_id IS NOT NULL
    ORDER BY x.id LIMIT 1) b ON true
WHERE p.name = '의류' AND p.parent_id IS NULL AND p.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM product.category s
      WHERE s.parent_id = p.id AND s.name = '치마' AND s.deleted_at IS NULL);

-- 치마의 필수 실측 항목은 총장 · 허리단면이다. 실측 항목이 아직 하나도 연결되지 않은 치마에만 연결한다
INSERT INTO product.category_measurement_item (category_id, measurement_item_id, sort_order, created_at, updated_at)
SELECT c.id, m.id, v.sort_order, now(), now()
FROM product.category c
JOIN product.category p ON p.id = c.parent_id AND p.name = '의류' AND p.parent_id IS NULL AND p.deleted_at IS NULL
JOIN (VALUES ('TOTAL_LENGTH', 1), ('WAIST_WIDTH', 2)) AS v(code, sort_order) ON true
JOIN product.measurement_item m ON m.code = v.code AND m.deleted_at IS NULL
WHERE c.name = '치마' AND c.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM product.category_measurement_item l WHERE l.category_id = c.id);
