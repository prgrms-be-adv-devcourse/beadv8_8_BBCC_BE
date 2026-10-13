package com.bbcc.kidly.boundedcontext.order.in.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.bbcc.kidly.boundedcontext.order.app.replica.ReplicaService;
import com.bbcc.kidly.boundedcontext.order.app.replica.dto.ChildChangedCommand;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildProfileEventListenerTest {

    private static final LocalDateTime OCCURRED_AT = LocalDateTime.of(2026, 10, 13, 9, 0);

    @Mock
    private ReplicaService replicaService;

    @InjectMocks
    private ChildProfileEventListener listener;

    @Captor
    private ArgumentCaptor<ChildChangedCommand> command;

    @Test
    @DisplayName("이벤트를 커맨드로 바꿔 넘긴다: 이름은 이벤트에 없어 null, 체형은 enum 이름")
    void convertsEvent() {
        listener.onChildChanged(event(BodyType.NORMAL, true));

        verify(replicaService).applyChildChanged(command.capture());
        assertThat(command.getValue()).isEqualTo(new ChildChangedCommand(
            1L, 1L, null, new BigDecimal("115.0"), new BigDecimal("20.0"), "NORMAL", true, OCCURRED_AT));
    }

    @Test
    @DisplayName("체형을 입력하지 않았으면 null로 넘긴다")
    void convertsNullBodyType() {
        listener.onChildChanged(event(null, false));

        verify(replicaService).applyChildChanged(command.capture());
        assertThat(command.getValue().bodyType()).isNull();
    }

    private static ChildChangedEvent event(BodyType bodyType, boolean deleted) {
        return new ChildChangedEvent(UUID.randomUUID(), OCCURRED_AT, new ChildChangedEvent.Data(
            1L, 1L, new BigDecimal("115.0"), new BigDecimal("20.0"), Gender.MALE, LocalDate.of(2019, 1, 1),
            bodyType, PreferredFit.LOOSE, deleted));
    }
}
