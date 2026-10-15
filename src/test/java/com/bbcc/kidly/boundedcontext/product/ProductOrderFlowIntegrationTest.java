package com.bbcc.kidly.boundedcontext.product;

import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.BUYER_ID;
import static com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture.OCCURRED_AT;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.app.order.OrderService;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.OrderDetail;
import com.bbcc.kidly.boundedcontext.order.app.order.dto.PlaceOrderCommand;
import com.bbcc.kidly.boundedcontext.order.app.port.OrderRepository;
import com.bbcc.kidly.boundedcontext.order.app.port.ProductReplicaRepository;
import com.bbcc.kidly.boundedcontext.order.domain.order.Order;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaProduct;
import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.product.domain.product.ProductOption;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 주문하기부터 결제완료 또는 재고 부족 취소까지를 실제 상품 재고 차감으로 잇는다.
 * 재고 차감됨과 재고 부족함은 테스트가 보내지 않고 상품 리스너가 보낸다.
 * 주문의 상품 복사본은 상품 쪽에 저장한 옵션과 같은 옵션 ID와 판매가로 만든다.
 */
@IntegrationTest
class ProductOrderFlowIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductReplicaRepository productReplicaRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ProductOptionSaver productOptionSaver;

    @Autowired
    private InventoryDeductionDb inventoryDeductionDb;

    @AfterEach
    void checkInvariants() {
        inventoryDeductionDb.assertInvariants();
    }

    @Test
    @DisplayName("주문하기를 부르면 상품이 재고를 줄이고 재고 차감됨을 보내, 예치금 결제를 거쳐 주문이 결제완료가 된다")
    void placedOrderIsPaidAfterStockDeduction() {
        ProductOption navy110 = productOptionSaver.saveOnSaleNavy110And120(10, 5).navy110();
        saveReplicaOf(navy110);
        Wallet wallet = Wallet.create(BUYER_ID);
        wallet.deposit(navy110.getPrice() * 2);
        walletRepository.save(wallet);

        OrderDetail order = place(navy110, 2);

        assertThat(inventoryDeductionDb.availableQty(navy110)).isEqualTo(8);
        assertThat(inventoryDeductionDb.countByStatus("DEDUCTED")).isEqualTo(1);
        assertThat(orderRepository.findById(order.orderId())).get()
            .extracting(Order::getStatus).isEqualTo(Order.Status.PAID);
    }

    @Test
    @DisplayName("INV-11 남은 재고보다 많이 주문하면 상품이 재고 부족함을 보내, 주문이 취소되고 주문상품에 재고 부족 사유가 남는다")
    void placedOrderIsCancelledWhenStockIsShort() {
        ProductOption navy110 = productOptionSaver.saveOnSaleNavy110And120(1, 5).navy110();
        saveReplicaOf(navy110);

        OrderDetail placed = place(navy110, 2);

        OrderDetail order = orderService.getOrder(BUYER_ID, placed.orderId());
        assertThat(inventoryDeductionDb.availableQty(navy110)).isEqualTo(1);
        assertThat(order.status()).isEqualTo("CANCELLED");
        assertThat(order.cancelReason()).isEqualTo("OUT_OF_STOCK");
        assertThat(order.items()).extracting(OrderDetail.Item::unavailableReason).containsExactly("OUT_OF_STOCK");
    }

    // 주문이 판매중으로 보도록, 상품 쪽에 저장한 옵션과 같은 옵션 ID와 판매가로 주문의 상품 복사본을 만든다
    private void saveReplicaOf(ProductOption option) {
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(option.getId(), option.getPrice(), "ON_SALE", false, OCCURRED_AT)));
    }

    private OrderDetail place(ProductOption option, int quantity) {
        return orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(option.getId(), null, quantity, option.getPrice())),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));
    }
}
