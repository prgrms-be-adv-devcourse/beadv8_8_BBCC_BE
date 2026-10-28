package com.bbcc.kidly.boundedcontext.product.out.product;

import static com.bbcc.kidly.boundedcontext.product.domain.inventory.QInventory.inventory;
import static com.bbcc.kidly.boundedcontext.product.domain.product.QProduct.product;
import static com.bbcc.kidly.boundedcontext.product.domain.product.QProductOption.productOption;

import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductQuery;
import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductSummary;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionStatus;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaSellerProductQuery implements SellerProductQuery {

    private final JPAQueryFactory queryFactory;

    // 정렬은 등록 최신순(id 내림차순)으로 고정한다. 보관 옵션은 조인에서 빼서 옵션 수 · 재고 합계 · 최저가에 들어가지 않는다
    @Override
    public Page<SellerProductSummary> findBySeller(Long sellerId, Pageable pageable) {
        NumberExpression<Long> optionCount = productOption.id.count();
        NumberExpression<Long> availableQuantity = inventory.availableQty.sumLong().coalesce(0L);
        NumberExpression<Long> minPrice = productOption.price.min();
        List<Tuple> rows = queryFactory
            .select(product.id, product.name, product.thumbnailUrl, product.status, product.createdAt,
                optionCount, availableQuantity, minPrice)
            .from(product)
            .leftJoin(productOption)
            .on(productOption.product.eq(product).and(productOption.saleStatus.ne(ProductOptionStatus.ARCHIVED)))
            .leftJoin(inventory).on(inventory.productOption.eq(productOption))
            .where(product.sellerId.eq(sellerId))
            .groupBy(product.id)
            .orderBy(product.id.desc())
            .offset(pageable.getOffset())
            .limit(pageable.getPageSize())
            .fetch();
        List<SellerProductSummary> content = new ArrayList<>();
        for (Tuple row : rows) {
            content.add(new SellerProductSummary(
                row.get(product.id),
                row.get(product.name),
                row.get(product.thumbnailUrl),
                row.get(product.status),
                row.get(optionCount),
                row.get(availableQuantity),
                row.get(minPrice),
                row.get(product.createdAt)));
        }
        var countQuery = queryFactory
            .select(product.count())
            .from(product)
            .where(product.sellerId.eq(sellerId));
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }
}
