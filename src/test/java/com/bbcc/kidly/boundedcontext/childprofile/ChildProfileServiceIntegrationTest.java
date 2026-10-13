package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.global.error.BusinessException;
import com.bbcc.kidly.global.error.CommonErrorCode;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@IntegrationTest
@RecordApplicationEvents
class ChildProfileServiceIntegrationTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final ChildProfileRequest REQUEST = new ChildProfileRequest(
        "첫째", LocalDate.of(2019, 1, 15), Gender.MALE, new BigDecimal("115.0"), new BigDecimal("20.0"),
        BodyType.NORMAL, PreferredFit.LOOSE);

    @Autowired
    private ChildProfileService childProfileService;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    @DisplayName("등록하면 자녀가 저장되고 전체 값을 담은 자녀 변경 이벤트가 발행된다")
    void register() {
        var child = childProfileService.register(MEMBER_ID, REQUEST);

        assertThat(child.growthRecordCount()).isEqualTo(1);
        assertThat(child.lastRecordedAt()).isNotNull();
        assertThat(childProfileRepository.findByIdAndMemberId(child.id(), MEMBER_ID)).isPresent();
        assertThat(events.stream(ChildChangedEvent.class)).singleElement().satisfies(event -> {
            assertThat(event.eventId()).isNotNull();
            assertThat(event.occurredAt()).isNotNull();
            assertThat(event.data().childId()).isEqualTo(child.id());
            assertThat(event.data().memberId()).isEqualTo(MEMBER_ID);
            assertThat(event.data().heightCm()).isEqualByComparingTo("115.0");
            assertThat(event.data().weightKg()).isEqualByComparingTo("20.0");
            assertThat(event.data().gender()).isEqualTo(Gender.MALE);
            assertThat(event.data().birthDate()).isEqualTo(LocalDate.of(2019, 1, 15));
            assertThat(event.data().bodyType()).isEqualTo(BodyType.NORMAL);
            assertThat(event.data().preferredFit()).isEqualTo(PreferredFit.LOOSE);
            assertThat(event.data().deleted()).isFalse();
        });
    }

    @Test
    @DisplayName("회원의 자녀 목록을 등록 순으로 준다")
    void getChildren() {
        var first = childProfileService.register(MEMBER_ID, REQUEST).id();
        var second = childProfileService.register(MEMBER_ID, REQUEST).id();
        childProfileService.register(OTHER_MEMBER_ID, REQUEST);

        assertThat(childProfileService.getChildren(MEMBER_ID)).extracting(result -> result.id())
            .containsExactly(first, second);
    }

    @Test
    @DisplayName("수정하면 바뀐 값이 저장되고 바뀐 뒤 전체 값으로 이벤트가 발행된다")
    void update() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();
        var changed = new ChildProfileRequest("첫째", LocalDate.of(2019, 1, 15), Gender.MALE,
            new BigDecimal("118.0"), new BigDecimal("21.0"), null, PreferredFit.SLIM);

        var child = childProfileService.update(MEMBER_ID, childId, changed);

        assertThat(child.heightCm()).isEqualByComparingTo("118.0");
        assertThat(child.bodyType()).isNull();
        assertThat(child.growthRecordCount()).isEqualTo(2);
        assertThat(events.stream(ChildChangedEvent.class)).hasSize(2).last().satisfies(event -> {
            assertThat(event.data().childId()).isEqualTo(childId);
            assertThat(event.data().heightCm()).isEqualByComparingTo("118.0");
            assertThat(event.data().bodyType()).isNull();
            assertThat(event.data().preferredFit()).isEqualTo(PreferredFit.SLIM);
            assertThat(event.data().deleted()).isFalse();
        });
    }

    @Test
    @DisplayName("삭제하면 삭제 직전 값과 deleted = true로 이벤트가 발행되고, 이후 조회는 404다")
    void delete() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();

        childProfileService.delete(MEMBER_ID, childId);

        assertThat(events.stream(ChildChangedEvent.class)).hasSize(2).last().satisfies(event -> {
            assertThat(event.data().childId()).isEqualTo(childId);
            assertThat(event.data().heightCm()).isEqualByComparingTo("115.0");
            assertThat(event.data().deleted()).isTrue();
        });
        assertNotFound(() -> childProfileService.getChild(MEMBER_ID, childId));
        assertThat(childProfileService.getChildren(MEMBER_ID)).isEmpty();
    }

    @Test
    @DisplayName("성장 이력을 오래된 순으로 준다")
    void getGrowthRecords() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();
        childProfileService.update(MEMBER_ID, childId, new ChildProfileRequest("첫째", LocalDate.of(2019, 1, 15),
            Gender.MALE, new BigDecimal("118.0"), new BigDecimal("20.0"), BodyType.NORMAL, PreferredFit.LOOSE));

        var records = childProfileService.getGrowthRecords(MEMBER_ID, childId);

        assertThat(records).hasSize(2);
        assertThat(records.get(0).heightCm()).isEqualByComparingTo("115.0");
        assertThat(records.get(1).heightCm()).isEqualByComparingTo("118.0");
    }

    @Test
    @DisplayName("남의 자녀는 조회 · 수정 · 삭제 · 성장 이력 모두 404이고 이벤트도 발행되지 않는다")
    void otherMembersChild() {
        var childId = childProfileService.register(MEMBER_ID, REQUEST).id();

        assertNotFound(() -> childProfileService.getChild(OTHER_MEMBER_ID, childId));
        assertNotFound(() -> childProfileService.update(OTHER_MEMBER_ID, childId, REQUEST));
        assertNotFound(() -> childProfileService.delete(OTHER_MEMBER_ID, childId));
        assertNotFound(() -> childProfileService.getGrowthRecords(OTHER_MEMBER_ID, childId));
        assertThat(events.stream(ChildChangedEvent.class)).hasSize(1);
        assertThat(childProfileService.getChild(MEMBER_ID, childId).name()).isEqualTo("첫째");
    }

    private static void assertNotFound(ThrowingCallable call) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.NOT_FOUND));
    }
}
