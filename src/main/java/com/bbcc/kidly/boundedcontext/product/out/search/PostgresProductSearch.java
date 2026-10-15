package com.bbcc.kidly.boundedcontext.product.out.search;

import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearch;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchItem;
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

    private static final String FROM_ON_SALE = """
        FROM product.product_search_document d
        WHERE d.status = 'ON_SALE'
        """;

    private static final String SELECT_ITEMS = """
        SELECT d.product_id, d.name, d.thumbnail_url, d.min_price, d.is_sold_out, d.is_size_free
        """;

    private static final String ORDER_AND_PAGE = """
        ORDER BY d.product_created_at DESC, d.product_id DESC
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
            SELECT_ITEMS + fromWhere + ORDER_AND_PAGE, params,
            (rs, rowNum) -> new ProductSearchItem(
                rs.getLong("product_id"),
                rs.getString("name"),
                rs.getString("thumbnail_url"),
                null,
                rs.getLong("min_price"),
                rs.getBoolean("is_sold_out"),
                rs.getBoolean("is_size_free")));

        Long total = jdbcTemplate.queryForObject("SELECT count(*) " + fromWhere, params, Long.class);
        return new PageImpl<>(content, pageable, total == null ? 0 : total);
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
        if (attributeValueIds == null) {
            return;
        }
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
