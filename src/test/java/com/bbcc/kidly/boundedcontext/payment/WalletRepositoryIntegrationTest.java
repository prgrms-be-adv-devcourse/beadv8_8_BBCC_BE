package com.bbcc.kidly.boundedcontext.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.payment.domain.Wallet;
import com.bbcc.kidly.boundedcontext.payment.domain.WalletRepository;
import com.bbcc.kidly.boundedcontext.payment.out.JpaWalletRepository;
import com.bbcc.kidly.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@IntegrationTest
class WalletRepositoryIntegrationTest {

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private JpaWalletRepository jpaWalletRepository;

    @Test
    @DisplayName("회원 ID로 지갑이 있는지 확인한다")
    void existsByMemberId() {
        walletRepository.save(Wallet.create(1L));

        assertThat(walletRepository.existsByMemberId(1L)).isTrue();
        assertThat(walletRepository.existsByMemberId(2L)).isFalse();
    }

    @Test
    @DisplayName("회원 ID로 지갑을 찾는다")
    void findByMemberId() {
        Wallet saved = walletRepository.save(Wallet.create(1L));

        assertThat(walletRepository.findByMemberId(1L)).hasValueSatisfying(
            found -> assertThat(found.getId()).isEqualTo(saved.getId()));
        assertThat(walletRepository.findByMemberId(2L)).isEmpty();
    }

    @Test
    @DisplayName("저장한 지갑은 잔액 0으로 조회된다")
    void save() {
        Wallet saved = walletRepository.save(Wallet.create(1L));

        assertThat(jpaWalletRepository.findById(saved.getId())).hasValueSatisfying(found -> {
            assertThat(found.getMemberId()).isEqualTo(1L);
            assertThat(found.getBalance()).isZero();
        });
    }

    @Test
    @DisplayName("한 회원에게 지갑을 두 개 만들 수 없다")
    void duplicateMemberId() {
        walletRepository.save(Wallet.create(1L));

        assertThatThrownBy(() -> walletRepository.save(Wallet.create(1L)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
