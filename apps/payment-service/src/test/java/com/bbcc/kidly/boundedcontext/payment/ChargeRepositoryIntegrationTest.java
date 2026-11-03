package com.bbcc.kidly.boundedcontext.payment;

import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.NOW;
import static com.bbcc.kidly.boundedcontext.payment.PaymentFixture.WALLET_ID;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.payment.domain.Charge;
import com.bbcc.kidly.boundedcontext.payment.domain.ChargeRepository;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@IntegrationTest
class ChargeRepositoryIntegrationTest {

    @Autowired
    private ChargeRepository chargeRepository;

    @Test
    @DisplayName("지갑의 충전만 최신순(ID 내림차순)으로 페이지 조회한다")
    void findAllByWalletIdOrderByIdDesc() {
        Charge first = chargeRepository.save(charge(WALLET_ID));
        chargeRepository.save(charge(20L));
        Charge second = chargeRepository.save(charge(WALLET_ID));
        Charge third = chargeRepository.save(charge(WALLET_ID));

        Page<Charge> page = chargeRepository.findAllByWalletIdOrderByIdDesc(WALLET_ID, PageRequest.of(0, 2));

        assertThat(page.getContent()).extracting(Charge::getId).containsExactly(third.getId(), second.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
        assertThat(chargeRepository.findAllByWalletIdOrderByIdDesc(WALLET_ID, PageRequest.of(1, 2)).getContent())
            .extracting(Charge::getId).containsExactly(first.getId());
    }

    private static Charge charge(Long walletId) {
        return Charge.request(walletId, UUID.randomUUID().toString(), 10_000L, NOW.plusMinutes(30));
    }
}
