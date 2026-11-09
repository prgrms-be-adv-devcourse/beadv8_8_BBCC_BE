package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildChangedRepublishService;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfile;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import java.time.LocalDateTime;
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
class ChildChangedRepublishServiceTest {

    @Mock
    private ChildProfileRepository childProfileRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChildChangedRepublishService childChangedRepublishService;

    @Test
    @DisplayName("삭제되지 않은 자녀마다 지금 값으로 child.changed를 새 eventId로 한 번씩 발행하고, 보낸 자녀 수를 돌려준다")
    void republishAll() {
        var first = withId(ChildProfileFixture.child(1L), 10L);
        var second = withId(ChildProfileFixture.child(2L), 20L);
        given(childProfileRepository.findAll()).willReturn(List.of(first, second));
        var before = LocalDateTime.now();

        int publishedCount = childChangedRepublishService.republishAll();

        assertThat(publishedCount).isEqualTo(2);
        var captor = ArgumentCaptor.forClass(ChildChangedEvent.class);
        then(eventPublisher).should(times(2)).publishEvent(captor.capture());
        var events = captor.getAllValues();
        assertThat(events)
            .extracting(event -> event.data().childId(), event -> event.data().memberId(),
                event -> event.data().deleted())
            .containsExactly(tuple(10L, 1L, false), tuple(20L, 2L, false));
        assertThat(events.getFirst().data())
            .isEqualTo(new ChildChangedEvent.Data(10L, 1L, ChildProfileFixture.HEIGHT, ChildProfileFixture.WEIGHT,
                Gender.MALE, ChildProfileFixture.BIRTH_DATE, BodyType.NORMAL, PreferredFit.LOOSE, false));
        assertThat(events.get(0).eventId()).isNotEqualTo(events.get(1).eventId());
        assertThat(events).allSatisfy(event -> assertThat(event.occurredAt()).isAfterOrEqualTo(before));
    }

    @Test
    @DisplayName("삭제되지 않은 자녀가 없으면 아무것도 발행하지 않고 0을 돌려준다")
    void republishNone() {
        given(childProfileRepository.findAll()).willReturn(List.of());

        assertThat(childChangedRepublishService.republishAll()).isZero();
        then(eventPublisher).should(never()).publishEvent(any(Object.class));
    }

    private static ChildProfile withId(ChildProfile child, Long id) {
        ReflectionTestUtils.setField(child, "id", id);
        return child;
    }
}
