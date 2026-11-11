package com.bbcc.kidly.boundedcontext.product.out.product;

import static com.bbcc.kidly.boundedcontext.product.domain.inventory.QInventory.inventory;
import static com.bbcc.kidly.boundedcontext.product.domain.product.QProduct.product;
import static com.bbcc.kidly.boundedcontext.product.domain.product.QProductOption.productOption;

import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductFilter;
import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductQuery;
import com.bbcc.kidly.boundedcontext.product.app.port.product.SellerProductSummary;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.QInventory;
import com.bbcc.kidly.boundedcontext.product.domain.inventory.StockDisplay;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOptionStatus;
import com.bbcc.kidly.boundedcontext.product.domain.product.QProductOption;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
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
    public Page<SellerProductSummary> findBySeller(Long sellerId, SellerProductFilter filter, Pageable pageable) {
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
            .where(conditions(sellerId, filter))
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
                0L,
                0L,
                row.get(minPrice),
                row.get(product.createdAt)));
        }
        var countQuery = queryFactory
            .select(product.count())
            .from(product)
            .where(conditions(sellerId, filter));
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    // 목록과 전체 건수가 같은 조건을 쓰도록 한 곳에서 만든다
    private BooleanBuilder conditions(Long sellerId, SellerProductFilter filter) {
        BooleanBuilder where = new BooleanBuilder(product.sellerId.eq(sellerId));
        if (filter.status() != null) {
            where.and(product.status.eq(filter.status()));
        }
        if (filter.lowStock()) {
            where.and(hasLowStockOption());
        }
        return where;
    }

    // 판매중 옵션 가운데 판매 가능 수량이 기준 이하인 것이 하나라도 있는지 본다. 재고 행이 없는 옵션은 0으로 본다
    private BooleanExpression hasLowStockOption() {
        QProductOption lowOption = new QProductOption("lowOption");
        QInventory lowInventory = new QInventory("lowInventory");
        return JPAExpressions.selectOne()
            .from(lowOption)
            .leftJoin(lowInventory).on(lowInventory.productOption.eq(lowOption))
            .where(lowOption.product.eq(product)
                .and(lowOption.saleStatus.eq(ProductOptionStatus.ON_SALE))
                .and(lowInventory.availableQty.coalesce(0).loe(StockDisplay.LOW_STOCK_THRESHOLD)))
            .exists();
    }
}
