package com.bbcc.kidly.boundedcontext.product.out.search;

import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearch;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchCondition;
import com.bbcc.kidly.boundedcontext.product.app.port.search.ProductSearchItem;
import java.util.List;
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
        return sql.toString();
    }

    // LIKE에서 특별한 뜻을 가진 \, %, _를 글자 그대로 찾도록 앞에 \를 붙인다. \를 가장 먼저 바꿔야 한다
    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
