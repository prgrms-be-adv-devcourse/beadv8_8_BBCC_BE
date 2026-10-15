package com.bbcc.kidly.boundedcontext.settlement;

import static com.bbcc.kidly.boundedcontext.settlement.SettlementFixture.CUTOFF_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.settlement.app.batch.SettlementSellerReader;
import com.bbcc.kidly.boundedcontext.settlement.app.port.SettlementSellerQuery;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementSellerReaderTest {

    @Mock
    private SettlementSellerQuery settlementSellerQuery;

    @Test
    @DisplayName("마지막 판매자 ID 다음부터 페이지씩 읽고, 덜 찬 페이지에서 끝낸다")
    void readPages() throws Exception {
        given(settlementSellerQuery.findSellerIdsToSettle(CUTOFF_AT, 0L, 2)).willReturn(List.of(10L, 11L));
        given(settlementSellerQuery.findSellerIdsToSettle(CUTOFF_AT, 11L, 2)).willReturn(List.of(12L));
        var reader = new SettlementSellerReader(settlementSellerQuery, CUTOFF_AT, 2);

        assertThat(reader.read()).isEqualTo(10L);
        assertThat(reader.read()).isEqualTo(11L);
        assertThat(reader.read()).isEqualTo(12L);
        assertThat(reader.read()).isNull();
        verify(settlementSellerQuery, times(2)).findSellerIdsToSettle(any(), any(), anyInt());
    }

    @Test
    @DisplayName("정산할 판매자가 없으면 바로 끝낸다")
    void readEmpty() throws Exception {
        given(settlementSellerQuery.findSellerIdsToSettle(CUTOFF_AT, 0L, 2)).willReturn(List.of());
        var reader = new SettlementSellerReader(settlementSellerQuery, CUTOFF_AT, 2);

        assertThat(reader.read()).isNull();
        assertThat(reader.read()).isNull();
        verify(settlementSellerQuery, times(1)).findSellerIdsToSettle(any(), any(), anyInt());
    }
}
