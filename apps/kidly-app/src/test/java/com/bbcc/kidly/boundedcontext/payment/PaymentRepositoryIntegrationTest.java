package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentItem;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class PaymentRepositoryIntegrationTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    @DisplayName("회원의 결제만 최신순(ID 내림차순)으로 페이지 조회한다")
    void findAllByMemberIdOrderByIdDesc() {
        Payment first = paymentRepository.save(completed(101L, MEMBER_ID, 1001L));
        paymentRepository.save(completed(102L, 2L, 1002L));
        Payment second = paymentRepository.save(completed(103L, MEMBER_ID, 1003L));
        Payment third = paymentRepository.save(completed(104L, MEMBER_ID, 1004L));

        Page<Payment> page = paymentRepository.findAllByMemberIdOrderByIdDesc(MEMBER_ID, PageRequest.of(0, 2));

        assertThat(page.getContent()).extracting(Payment::getId).containsExactly(third.getId(), second.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
        assertThat(paymentRepository.findAllByMemberIdOrderByIdDesc(MEMBER_ID, PageRequest.of(1, 2)).getContent())
            .extracting(Payment::getId).containsExactly(first.getId());
    }

    @Test
    @DisplayName("ID로 결제를 찾는다")
    void findById() {
        Payment saved = paymentRepository.save(completed(101L, MEMBER_ID, 1001L));

        assertThat(paymentRepository.findById(saved.getId())).hasValueSatisfying(
            found -> assertThat(found.getOrderId()).isEqualTo(101L));
    }

    private static Payment completed(Long orderId, Long memberId, Long orderItemId) {
        return Payment.complete(orderId, WALLET_ID, memberId, List.of(PaymentItem.of(orderItemId, 10_000L)), NOW);
    }
}
