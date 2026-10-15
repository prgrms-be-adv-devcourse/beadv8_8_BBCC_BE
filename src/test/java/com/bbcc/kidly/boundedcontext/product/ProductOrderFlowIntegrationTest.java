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
 * 주문하기부터 결제완료까지를 실제 상품 재고 차감으로 잇는다. 재고 차감됨은 테스트가 보내지 않고 상품 리스너가 보낸다.
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
        productReplicaRepository.save(ReplicaProduct.create(
            ReplicaFixture.product(navy110.getId(), navy110.getPrice(), "ON_SALE", false, OCCURRED_AT)));
        Wallet wallet = Wallet.create(BUYER_ID);
        wallet.deposit(navy110.getPrice() * 2);
        walletRepository.save(wallet);

        OrderDetail order = orderService.place(BUYER_ID, new PlaceOrderCommand(
            List.of(new PlaceOrderCommand.Item(navy110.getId(), null, 2, navy110.getPrice())),
            new PlaceOrderCommand.ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시 테스트로 1")));

        assertThat(inventoryDeductionDb.availableQty(navy110)).isEqualTo(8);
        assertThat(inventoryDeductionDb.countByStatus("DEDUCTED")).isEqualTo(1);
        assertThat(orderRepository.findById(order.orderId())).get()
            .extracting(Order::getStatus).isEqualTo(Order.Status.PAID);
    }
}
