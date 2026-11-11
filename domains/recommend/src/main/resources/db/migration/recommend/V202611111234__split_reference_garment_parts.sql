-- #1111 기준 옷 칸을 상의 · 하의 2개에서 상의 · 바지 · 아우터 · 원피스 · 치마 5개로 나눈다.
-- 상의 칸에 저장된 원피스 · 아우터와 바지 칸에 저장된 치마를 그 종류의 칸으로 옮긴다. 스키마는 바꾸지 않는다.
-- 종류는 세부 종류 카테고리가 들어 있는 상품 복사본의 카테고리 경로 이름으로 판단한다. 앱도 경로 전체의 이름으로 판단한다.
-- 경로를 찾지 못한 줄은 그대로 둔다. 옮긴 줄의 실측이 새 칸 규칙에 맞지 않아도 그대로 두고, 다음 저장 때 새 규칙으로 검증한다.
-- 지금은 자녀마다 상의 · 하의 칸이 한 줄씩이고 새 칸에는 줄이 없으므로 옮겨도 (child_profile_id, part) UNIQUE와 부딪히지 않는다.
UPDATE recommend.reference_garment g
SET part = 'DRESS'
WHERE g.part = 'TOP'
  AND EXISTS (
    SELECT 1
    FROM recommend.replica_product_category_path leaf
    JOIN recommend.replica_product_category_path p ON p.product_id = leaf.product_id
    WHERE leaf.category_id = g.category_id AND p.category_name = '원피스');

UPDATE recommend.reference_garment g
SET part = 'OUTER'
WHERE g.part = 'TOP'
  AND EXISTS (
    SELECT 1
    FROM recommend.replica_product_category_path leaf
    JOIN recommend.replica_product_category_path p ON p.product_id = leaf.product_id
    WHERE leaf.category_id = g.category_id AND p.category_name = '아우터');

UPDATE recommend.reference_garment g
SET part = 'SKIRT'
WHERE g.part = 'BOTTOM'
  AND EXISTS (
    SELECT 1
    FROM recommend.replica_product_category_path leaf
    JOIN recommend.replica_product_category_path p ON p.product_id = leaf.product_id
    WHERE leaf.category_id = g.category_id AND p.category_name = '치마');
