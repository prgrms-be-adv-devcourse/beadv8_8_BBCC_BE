package com.bbcc.kidly.boundedcontext.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.bbcc.kidly.boundedcontext.settlement.app.SellerSettlementService;
import com.bbcc.kidly.boundedcontext.settlement.app.SettlementBusinessCalendar;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SellerWithdrawalBlockerResult;
import com.bbcc.kidly.boundedcontext.settlement.app.dto.SettlementTargetResult;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementHolidayLookupException;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetQuery;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementTargetRow;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementItemStatus;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementRepository;
import com.bbcc.kidly.boundedcontext.settlement.domain.SettlementStatus;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class SellerSettlementServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementItemRepository settlementItemRepository;

    @Mock
    private SettlementTargetQuery settlementTargetQuery;

    @Mock
    private SettlementBusinessCalendar businessCalendar;

    @InjectMocks
    private SellerSettlementService sellerSettlementService;

    @Test
    @DisplayName("없거나 다른 판매자의 정산서 상세는 404다")
    void detailNotFound() {
        given(settlementRepository.getByIdAndSellerId(1L, 99L)).willCallRealMethod();
        given(settlementRepository.findByIdAndSellerId(1L, 99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sellerSettlementService.getSettlement(99L, 1L))
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }

    @Test
    @DisplayName("정산 대상마다 확정일 다음 영업일을 예정일로 준다")
    void targetsWithExpectedPayoutOn() {
        var pageable = PageRequest.of(0, 20);
        var row = new SettlementTargetRow(10L, 5L, 30_000L, LocalDateTime.of(2026, 3, 6, 15, 0));
        given(settlementTargetQuery.findPending(7L, pageable)).willReturn(new PageImpl<>(List.of(row)));
        given(businessCalendar.nextBusinessDayAfter(LocalDate.of(2026, 3, 6))).willReturn(LocalDate.of(2026, 3, 9));

        var targets = sellerSettlementService.getTargets(7L, pageable);

        assertThat(targets.getContent()).singleElement()
            .extracting(SettlementTargetResult::expectedPayoutOn).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    @DisplayName("공휴일을 조회하지 못하면 목록은 그대로 주고 예정일만 null이다")
    void targetsWithoutExpectedPayoutOnWhenLookupFails() {
        var pageable = PageRequest.of(0, 20);
        var row = new SettlementTargetRow(10L, 5L, 30_000L, LocalDateTime.of(2026, 3, 6, 15, 0));
        given(settlementTargetQuery.findPending(7L, pageable)).willReturn(new PageImpl<>(List.of(row)));
        given(businessCalendar.nextBusinessDayAfter(LocalDate.of(2026, 3, 6)))
            .willThrow(new SettlementHolidayLookupException("공휴일 API 인증키가 없습니다"));

        var targets = sellerSettlementService.getTargets(7L, pageable);

        assertThat(targets.getContent()).singleElement().satisfies(target -> {
            assertThat(target.orderItemId()).isEqualTo(10L);
            assertThat(target.expectedPayoutOn()).isNull();
        });
    }

    @Test
    @DisplayName("미정산 항목도 미지급 정산서도 없으면 탈퇴를 막을 사유가 없다")
    void noWithdrawalBlockers() {
        givenWithdrawalCounts(0L, 0L);

        assertThat(sellerSettlementService.getWithdrawalBlockers(7L)).isEmpty();
    }

    @Test
    @DisplayName("PENDING 항목과 COMPLETED · FAILED 정산서를 세어 사유 순서대로 준다")
    void withdrawalBlockers() {
        givenWithdrawalCounts(2L, 1L);

        assertThat(sellerSettlementService.getWithdrawalBlockers(7L)).containsExactly(
            new SellerWithdrawalBlockerResult("UNSETTLED_ITEM", 2L),
            new SellerWithdrawalBlockerResult("UNPAID_SETTLEMENT", 1L));
    }

    @Test
    @DisplayName("0건인 사유는 빼고 준다")
    void withdrawalBlockersWithoutZeroCount() {
        givenWithdrawalCounts(0L, 3L);

        assertThat(sellerSettlementService.getWithdrawalBlockers(7L)).containsExactly(
            new SellerWithdrawalBlockerResult("UNPAID_SETTLEMENT", 3L));
    }

    // 엄격한 스텁이라 다른 상태(SETTLED · PAID)로 세면 테스트가 실패한다
    private void givenWithdrawalCounts(long pendingItems, long unpaidSettlements) {
        given(settlementItemRepository.countBySellerIdAndStatus(7L, SettlementItemStatus.PENDING))
            .willReturn(pendingItems);
        given(settlementRepository.countBySellerIdAndStatusIn(
            7L, List.of(SettlementStatus.COMPLETED, SettlementStatus.FAILED))).willReturn(unpaidSettlements);
    }
}
