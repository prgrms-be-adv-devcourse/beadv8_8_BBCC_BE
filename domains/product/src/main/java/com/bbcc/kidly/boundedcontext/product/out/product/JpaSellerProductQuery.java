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
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaSellerProductQuery implements SellerProductQuery {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<SellerProductSummary> findBySeller(Long sellerId, SellerProductFilter filter, Pageable pageable) {
        NumberExpression<Long> optionCount = productOption.id.count();
        NumberExpression<Long> availableQuantity = inventory.availableQty.sumLong().coalesce(0L);
        NumberExpression<Long> minPrice = productOption.price.min();
        NumberExpression<Integer> quantity = inventory.availableQty.coalesce(0);
        BooleanExpression onSale = productOption.saleStatus.eq(ProductOptionStatus.ON_SALE);
        NumberExpression<Long> soldOutCount = countIf(onSale.and(quantity.eq(0)));
        NumberExpression<Long> lowStockCount = countIf(onSale.and(quantity.gt(0))
            .and(quantity.loe(StockDisplay.LOW_STOCK_THRESHOLD)));
        var rows = queryFactory
            .select(product.id, product.name, product.thumbnailUrl, product.status, product.createdAt,
                optionCount, availableQuantity, soldOutCount, lowStockCount, minPrice)
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
        var content = new ArrayList<SellerProductSummary>();
        for (Tuple row : rows) {
            content.add(new SellerProductSummary(
                row.get(product.id),
                row.get(product.name),
                row.get(product.thumbnailUrl),
                row.get(product.status),
                row.get(optionCount),
                row.get(availableQuantity),
                row.get(soldOutCount),
                row.get(lowStockCount),
                row.get(minPrice),
                row.get(product.createdAt)));
        }
        var countQuery = queryFactory
            .select(product.count())
            .from(product)
            .where(conditions(sellerId, filter));
        return PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne);
    }

    private NumberExpression<Long> countIf(BooleanExpression condition) {
        return new CaseBuilder().when(condition).then(1L).otherwise(0L).sumLong();
    }

    private BooleanBuilder conditions(Long sellerId, SellerProductFilter filter) {
        var where = new BooleanBuilder(product.sellerId.eq(sellerId));
        if (filter.status() != null) {
            where.and(product.status.eq(filter.status()));
        }
        if (filter.lowStock()) {
            where.and(hasLowStockOption());
        }
        return where;
    }

    private BooleanExpression hasLowStockOption() {
        var lowOption = new QProductOption("lowOption");
        var lowInventory = new QInventory("lowInventory");
        return JPAExpressions.selectOne()
            .from(lowOption)
            .leftJoin(lowInventory).on(lowInventory.productOption.eq(lowOption))
            .where(lowOption.product.eq(product)
                .and(lowOption.saleStatus.eq(ProductOptionStatus.ON_SALE))
                .and(lowInventory.availableQty.coalesce(0).loe(StockDisplay.LOW_STOCK_THRESHOLD)))
            .exists();
    }
}
