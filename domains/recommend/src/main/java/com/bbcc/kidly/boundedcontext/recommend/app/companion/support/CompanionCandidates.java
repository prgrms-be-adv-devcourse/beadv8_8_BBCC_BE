package com.bbcc.kidly.boundedcontext.recommend.app.companion.support;

import com.bbcc.kidly.boundedcontext.recommend.app.port.companion.CompanionAiRequest;
import com.bbcc.kidly.boundedcontext.recommend.domain.companion.CompanionCandidate;
import java.util.List;
import java.util.Map;

/**
 * 후보를 읽는 조각이 트랜잭션 안에서 읽어 넘기는 값이다. 판매자 이름은 판매자 ID로 찾는다.
 */
public record CompanionCandidates(
    CompanionAiRequest aiRequest,
    List<CompanionCandidate> ranked,
    Map<Long, String> businessNames
) {
}
