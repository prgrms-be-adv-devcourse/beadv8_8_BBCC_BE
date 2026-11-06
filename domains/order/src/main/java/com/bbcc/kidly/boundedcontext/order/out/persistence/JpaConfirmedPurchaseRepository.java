package com.bbcc.kidly.boundedcontext.order.out.persistence;

import com.bbcc.kidly.boundedcontext.order.app.port.ConfirmedPurchases;
import com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface JpaConfirmedPurchaseRepository extends Repository<OrderItem, Long>, ConfirmedPurchases {

    @Override
    @Query("""
        select coalesce(max(i.id), 0) from OrderItem i
        where i.status = com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem.Status.CONFIRMED
          and i.confirmedAt is not null
        """)
    long upperBound();

    @Override
    @Query("""
        select i.id as orderItemId, o.id as orderId, o.memberId as memberId, i.product.sellerId as sellerId,
               i.child.childId as childId, i.product.productId as productId, i.product.optionId as optionId,
               i.product.size as size, i.quantity as quantity, i.amount as amount, i.confirmedAt as confirmedAt,
               i.confirmType as confirmType
        from OrderItem i join i.order o
        where i.status = com.bbcc.kidly.boundedcontext.order.domain.order.OrderItem.Status.CONFIRMED
          and i.confirmedAt is not null and i.id > :afterId and i.id <= :throughId
        order by i.id asc
        """)
    List<Purchase> findAfter(long afterId, long throughId, Limit limit);
}
