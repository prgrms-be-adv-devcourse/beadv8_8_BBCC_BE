package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.any;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildGrowthRecordedRepublishService;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildGrowthRecordedEvent;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChildGrowthRecordedRepublishServiceTest {

    @Mock
    private ChildProfileRepository repository;

    @Mock
    private ApplicationEventPublisher publisher;

    @InjectMocks
    private ChildGrowthRecordedRepublishService service;

    @Test
    @DisplayName("삭제되지 않은 자녀의 성장 이력마다 원래 기록 시각으로 한 번씩 발행하고, 보낸 이력 수를 돌려준다")
    void republishesEveryRecordInOrder() {
        var first = ChildProfileFixture.child(1L);
        var second = ChildProfileFixture.child(2L);
        first.update(ChildProfileFixture.updateInfo("첫째", new BigDecimal("118"), ChildProfileFixture.WEIGHT,
            BodyType.NORMAL, PreferredFit.LOOSE), ChildProfileFixture.NOW.plusDays(30));
        ReflectionTestUtils.setField(first, "id", 10L);
        ReflectionTestUtils.setField(second, "id", 20L);
        ReflectionTestUtils.setField(first.getGrowthRecords().get(0), "id", 1L);
        ReflectionTestUtils.setField(first.getGrowthRecords().get(1), "id", 2L);
        ReflectionTestUtils.setField(second.getGrowthRecords().get(0), "id", 3L);
        given(repository.findAllWithGrowthRecords()).willReturn(List.of(first, second));

        assertThat(service.republishAll()).isEqualTo(3);
        var captor = ArgumentCaptor.forClass(ChildGrowthRecordedEvent.class);
        then(publisher).should(times(3)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(event -> event.data().growthRecordId())
            .containsExactly(1L, 2L, 3L);
        assertThat(captor.getAllValues()).extracting(event -> event.data().childId()).containsExactly(10L, 10L, 20L);
        assertThat(captor.getAllValues()).extracting(ChildGrowthRecordedEvent::occurredAt)
            .containsExactly(ChildProfileFixture.NOW, ChildProfileFixture.NOW.plusDays(30), ChildProfileFixture.NOW);
    }

    @Test
    @DisplayName("삭제되지 않은 자녀가 없으면 아무것도 발행하지 않고 0을 돌려준다")
    void noChildren() {
        given(repository.findAllWithGrowthRecords()).willReturn(List.of());
        assertThat(service.republishAll()).isZero();
        then(publisher).should(never()).publishEvent(any(Object.class));
    }
}
