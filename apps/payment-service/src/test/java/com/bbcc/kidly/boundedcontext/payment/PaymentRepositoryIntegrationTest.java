package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.MEMBER_ID;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.DailyPaymentTotal;
import com.bbcc.kidly.boundedcontext.payment.domain.Payment;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentItem;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentRepository;
import com.bbcc.kidly.boundedcontext.payment.domain.PaymentStatus;
import com.bbcc.kidly.shared.payment.domain.PaymentFailureCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
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

    @Test
    @DisplayName("결제일별로 결제 금액 · 건수를 더하고, 실패 결제와 기간 밖 결제는 뺀다")
    void sumDailyPaidBetween() {
        paymentRepository.save(paidAt(101L, 1001L, 10_000L, LocalDateTime.of(2026, 11, 9, 23, 59, 59)));
        paymentRepository.save(paidAt(102L, 1002L, 10_000L, LocalDateTime.of(2026, 11, 10, 0, 0)));
        paymentRepository.save(paidAt(103L, 1003L, 25_000L, LocalDateTime.of(2026, 11, 10, 18, 30)));
        Payment refunded = paidAt(104L, 1004L, 7_000L, LocalDateTime.of(2026, 11, 11, 9, 0));
        refunded.refund(1004L, 7_000L);
        paymentRepository.save(refunded);
        paymentRepository.save(paidAt(105L, 1005L, 99_000L, LocalDateTime.of(2026, 11, 12, 0, 0)));
        paymentRepository.save(Payment.fail(106L, WALLET_ID, MEMBER_ID, 50_000L,
            PaymentFailureCode.INSUFFICIENT_BALANCE));

        List<DailyPaymentTotal> daily = paymentRepository.sumDailyPaidBetween(
            LocalDateTime.of(2026, 11, 10, 0, 0), LocalDateTime.of(2026, 11, 12, 0, 0));

        assertThat(daily).containsExactly(
            new DailyPaymentTotal(LocalDate.of(2026, 11, 10), 35_000L, 2L),
            new DailyPaymentTotal(LocalDate.of(2026, 11, 11), 7_000L, 1L));
    }

    @Test
    @DisplayName("부분 · 전액 환불된 결제 수를 센다")
    void countByStatusIn() {
        paymentRepository.save(completed(101L, MEMBER_ID, 1001L));
        Payment partial = Payment.complete(102L, WALLET_ID, MEMBER_ID,
            List.of(PaymentItem.of(1002L, 10_000L), PaymentItem.of(1003L, 5_000L)), NOW);
        partial.refund(1002L, 10_000L);
        paymentRepository.save(partial);
        Payment full = completed(103L, MEMBER_ID, 1004L);
        full.refund(1004L, 10_000L);
        paymentRepository.save(full);

        assertThat(paymentRepository.countByStatusIn(
            EnumSet.of(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED))).isEqualTo(2);
    }

    private static Payment paidAt(Long orderId, Long orderItemId, long amount, LocalDateTime paidAt) {
        return Payment.complete(orderId, WALLET_ID, MEMBER_ID, List.of(PaymentItem.of(orderItemId, amount)), paidAt);
    }

    private static Payment completed(Long orderId, Long memberId, Long orderItemId) {
        return Payment.complete(orderId, WALLET_ID, memberId, List.of(PaymentItem.of(orderItemId, 10_000L)), NOW);
    }
}
