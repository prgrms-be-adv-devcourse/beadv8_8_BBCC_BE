package com.bbcc.kidly.boundedcontext.product.out.search;

import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearch;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchItem;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchSort;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 검색용 표(product_search_document)를 PostgreSQL 쿼리로 읽어 상품 목록을 만든다.
 */
@Component
public class PostgresProductSearch implements ProductSearch {

    // 상호명은 판매자 복사본에서 붙인다. 복사본이 없는 판매자의 상품도 빠지지 않게 LEFT JOIN하고,
    // 판매자 ID가 복사본의 PK라 행이 늘지 않으므로 개수 쿼리도 같은 FROM을 쓴다
    private static final String FROM_ON_SALE = """
        FROM product.product_search_document d
        LEFT JOIN product.replica_seller rs ON rs.seller_id = d.seller_id
        WHERE d.status = 'ON_SALE'
        """;

    private static final String SELECT_ITEMS = """
        SELECT d.product_id, d.name, d.thumbnail_url, rs.business_name, d.min_price, d.is_sold_out, d.is_size_free
        """;

    private static final String PAGE = """
        LIMIT :limit OFFSET :offset
        """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PostgresProductSearch(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<ProductSearchItem> search(ProductSearchCondition condition, Pageable pageable) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("limit", pageable.getPageSize())
            .addValue("offset", pageable.getOffset());
        String fromWhere = fromWhere(condition, params);

        List<ProductSearchItem> content = jdbcTemplate.query(
            SELECT_ITEMS + fromWhere + orderBy(condition.sort()) + PAGE, params,
            (rs, rowNum) -> new ProductSearchItem(
                rs.getLong("product_id"),
                rs.getString("name"),
                rs.getString("thumbnail_url"),
                rs.getString("business_name"),
                rs.getLong("min_price"),
                rs.getBoolean("is_sold_out"),
                rs.getBoolean("is_size_free")));

        Long total = jdbcTemplate.queryForObject("SELECT count(*) " + fromWhere, params, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
    }

    // 정렬은 enum으로만 고르므로 사용자가 보낸 글자가 SQL에 들어가지 않는다. 정렬 값이 같으면 상품 ID가 큰 상품이 먼저 온다
    private String orderBy(ProductSearchSort sort) {
        String column = switch (sort) {
            case NEWEST -> "d.product_created_at DESC";
            case PRICE_ASC -> "d.min_price ASC";
            case PRICE_DESC -> "d.min_price DESC";
        };
        return "ORDER BY " + column + ", d.product_id DESC\n";
    }

    // 조건이 있는 것만 WHERE에 더하고, 값은 모두 바인드 파라미터로 넘긴다
    private String fromWhere(ProductSearchCondition condition, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder(FROM_ON_SALE);
        if (condition.categoryId() != null) {
            sql.append("AND d.category_id = :categoryId\n");
            params.addValue("categoryId", condition.categoryId());
        }
        if (condition.keyword() != null && !condition.keyword().isBlank()) {
            sql.append("AND d.name ILIKE :keyword ESCAPE '\\'\n");
            params.addValue("keyword", "%" + escapeLike(condition.keyword()) + "%");
        }
        appendAttributeConditions(sql, condition.attributeValueIds(), params);
        return sql.toString();
    }

    // 항목 code마다 괄호 묶음 하나를 AND로 더한다. 묶음 안에서는 고른 값을 OR로 이어 하나만 맞아도 통과시킨다.
    // JSON 문자열을 직접 만들지 않고 code와 값 ID를 모두 바인드 파라미터로 넘긴다
    private void appendAttributeConditions(
        StringBuilder sql, Map<String, List<Long>> attributeValueIds, MapSqlParameterSource params) {
        int codeIndex = 0;
        for (Map.Entry<String, List<Long>> entry : attributeValueIds.entrySet()) {
            List<Long> valueIds = entry.getValue();
            if (valueIds == null || valueIds.isEmpty()) {
                continue;
            }
            String codeParam = "attrCode" + codeIndex;
            params.addValue(codeParam, entry.getKey());
            sql.append("AND (");
            for (int valueIndex = 0; valueIndex < valueIds.size(); valueIndex++) {
                String valueParam = "attrValue" + codeIndex + "_" + valueIndex;
                params.addValue(valueParam, valueIds.get(valueIndex));
                if (valueIndex > 0) {
                    sql.append(" OR ");
                }
                sql.append("d.attributes @> jsonb_build_object(CAST(:").append(codeParam)
                    .append(" AS text), jsonb_build_array(CAST(:").append(valueParam).append(" AS bigint)))");
            }
            sql.append(")\n");
            codeIndex++;
        }
    }

    // LIKE에서 특별한 뜻을 가진 \, %, _를 글자 그대로 찾도록 앞에 \를 붙인다. \를 가장 먼저 바꿔야 한다
    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
