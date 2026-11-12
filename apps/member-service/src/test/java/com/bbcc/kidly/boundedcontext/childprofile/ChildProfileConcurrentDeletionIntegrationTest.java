package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileRequest;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileUpdateRequest;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileErrorCode;
import com.bbcc.kidly.boundedcontext.childprofile.domain.GuardianVerificationRepository;
import com.bbcc.kidly.member.support.error.BusinessException;
import com.bbcc.kidly.shared.childprofile.domain.BodyType;
import com.bbcc.kidly.shared.childprofile.domain.Gender;
import com.bbcc.kidly.shared.childprofile.domain.PreferredFit;
import com.bbcc.kidly.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@IntegrationTest
class ChildProfileConcurrentDeletionIntegrationTest {
    private static final Long MEMBER_ID = 1L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(2019, 1, 15);

    @Autowired private ChildProfileService service;
    @Autowired private GuardianVerificationRepository verifications;

    @Test
    @DisplayName("두 성장 이력을 동시에 삭제해도 하나는 남고 프로필 키와 일치한다")
    void concurrentDeletionKeepsOneRecord() throws Exception {
        verifications.save(GuardianVerificationFixture.verification(MEMBER_ID));
        var childId = service.register(MEMBER_ID, new ChildProfileRequest("첫째", BIRTH_DATE, Gender.MALE,
            new BigDecimal("115.0"), new BigDecimal("20.0"), BodyType.NORMAL, PreferredFit.LOOSE, true, true)).id();
        service.update(MEMBER_ID, childId, new ChildProfileUpdateRequest("첫째", BIRTH_DATE, Gender.MALE,
            new BigDecimal("117.0"), new BigDecimal("20.0"), BodyType.NORMAL, PreferredFit.LOOSE));
        var records = service.getGrowthRecords(MEMBER_ID, childId);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> delete(start, childId, records.getFirst().id()));
            var second = executor.submit(() -> delete(start, childId, records.getLast().id()));
            start.countDown();
            assertThat(Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(null, ChildProfileErrorCode.ONLY_GROWTH_RECORD);
        }
        var remaining = service.getGrowthRecords(MEMBER_ID, childId);
        assertThat(remaining).hasSize(1);
        assertThat(service.getChild(MEMBER_ID, childId).heightCm())
            .isEqualByComparingTo(remaining.getFirst().heightCm());
    }

    private ChildProfileErrorCode delete(CountDownLatch start, Long childId, Long recordId)
        throws InterruptedException {
        start.await();
        try {
            service.deleteGrowthRecord(MEMBER_ID, childId, recordId);
            return null;
        } catch (BusinessException e) {
            assertThat(e.getErrorCode()).isEqualTo(ChildProfileErrorCode.ONLY_GROWTH_RECORD);
            return ChildProfileErrorCode.ONLY_GROWTH_RECORD;
        }
    }
}
