package com.bbcc.kidly.boundedcontext.childprofile;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.childprofile.app.ChildProfileDeadLetterService;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileDeadLetterMessage;
import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileDeadLetterResult;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileDeadLetterRepository;
import com.bbcc.kidly.boundedcontext.childprofile.domain.ChildProfileRepository;
import com.bbcc.kidly.shared.childprofile.event.ChildChangedEvent;
import com.bbcc.kidly.shared.member.event.MemberWithdrawnEvent;
import com.bbcc.kidly.support.CommittedEventRecorder;
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
class ChildProfileDeadLetterReprocessIntegrationTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long MEMBER_ID = 1L;

    @Autowired
    private ChildProfileDeadLetterService service;

    @Autowired
    private ChildProfileDeadLetterRepository deadLetterRepository;

    @Autowired
    private ChildProfileRepository childProfileRepository;

    @Autowired
    private CommittedEventRecorder committedEvents;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    @DisplayName("두 관리자가 동시에 재처리해도 처리기는 한 번만 돌고 둘 다 REPROCESSED를 받는다")
    void reprocessesOnceConcurrently(CapturedOutput output) throws Exception {
        childProfileRepository.save(ChildProfileFixture.child(MEMBER_ID));
        var payload = jsonMapper.writeValueAsString(new MemberWithdrawnEvent(UUID.randomUUID(), ChildProfileFixture.NOW,
            new MemberWithdrawnEvent.Data(MEMBER_ID)));
        var id = deadLetterRepository.save(new ChildProfileDeadLetterMessage("kidly.member.events.childprofile.dlt", 0,
            1L, "kidly.member.events", 0, 1L, String.valueOf(MEMBER_ID), "member.withdrawn",
            UUID.randomUUID().toString(), payload, "tools.jackson.core.JacksonException").toDeadLetter()).getId();
        committedEvents.clear();

        var results = reprocessConcurrently(id);

        assertThat(results).extracting(ChildProfileDeadLetterResult::status).containsOnly("REPROCESSED");
        var deadLetter = deadLetterRepository.getById(id);
        assertThat(deadLetter.getReplayCount()).isOne();
        assertThat(deadLetter.getLastReplayFailureType()).isNull();
        assertThat(childProfileRepository.findAllByMemberId(MEMBER_ID)).isEmpty();
        assertThat(committedEvents.events(ChildChangedEvent.class)).hasSize(1);
        assertThat(output).doesNotContain("DLT 재처리 실패");
    }

    private List<ChildProfileDeadLetterResult> reprocessConcurrently(Long id) throws Exception {
        var start = new CountDownLatch(1);
        Callable<ChildProfileDeadLetterResult> reprocess = () -> {
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
