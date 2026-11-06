package com.bbcc.kidly.boundedcontext.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.seller.app.SellerDeadLetterService;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerDeadLetterResult;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerApplicationRepository;
import com.bbcc.kidly.boundedcontext.seller.domain.SellerDeadLetterRepository;
import com.bbcc.kidly.shared.member.event.MemberChangedEvent;
import com.bbcc.kidly.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class SellerDeadLetterReprocessIntegrationTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private SellerDeadLetterService service;

    @Autowired
    private SellerDeadLetterRepository deadLetterRepository;

    @Autowired
    private SellerApplicationRepository sellerApplicationRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("두 관리자가 동시에 재처리해도 처리기는 한 번만 돌고 둘 다 REPROCESSED를 받는다")
    void reprocessesOnceConcurrently(CapturedOutput output) throws Exception {
        var applicationId = sellerApplicationRepository.save(SellerFixture.application(MEMBER_ID)).getId();
        var payload = jsonMapper.writeValueAsString(new MemberChangedEvent(UUID.randomUUID(), SellerFixture.NOW, new
            MemberChangedEvent.Data(MEMBER_ID, "하은맘")));
        var id = deadLetterRepository.save(new SellerDeadLetterMessage("kidly.member.events.seller.dlt", 0, 1L,
            "kidly.member.events", 0, 1L, String.valueOf(MEMBER_ID), "member.changed", UUID.randomUUID().toString(),
            payload, "tools.jackson.core.JacksonException").toDeadLetter()).getId();

        var results = reprocessConcurrently(id);

        assertThat(results).extracting(SellerDeadLetterResult::status).containsOnly("REPROCESSED");
        var deadLetter = deadLetterRepository.getById(id);
        assertThat(deadLetter.getReplayCount()).isOne();
        assertThat(deadLetter.getLastReplayFailureType()).isNull();
        assertThat(sellerApplicationRepository.getById(applicationId).getApplicantNickname()).isEqualTo("하은맘");
        assertThat(output).doesNotContain("DLT 재처리 실패");
    }

    private List<SellerDeadLetterResult> reprocessConcurrently(Long id) throws Exception {
        var start = new CountDownLatch(1);
        Callable<SellerDeadLetterResult> reprocess = () -> {
            start.await();
            return service.reprocess(id, ADMIN_ID);
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(reprocess);
            var second = executor.submit(reprocess);
            start.countDown();
            return List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
        }
    }
}
