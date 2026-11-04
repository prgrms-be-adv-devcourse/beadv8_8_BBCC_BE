-- #791 상품 목록 검색은 검색 서비스가 자기 색인으로 맡으므로, 상품이 따로 두던 검색용 표를 지운다.
-- 이 표를 쓰던 엔티티와 코드는 #796에서 이미 지웠다.
-- kidly-app과 상품 서비스가 함께 도는 동안 두 앱이 각자 이 파일을 적용하므로, 두 번 적용돼도 안전하게 IF EXISTS로 쓴다.
DROP TABLE IF EXISTS product.product_search_document;
