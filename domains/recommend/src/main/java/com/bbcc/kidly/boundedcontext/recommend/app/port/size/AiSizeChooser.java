package com.bbcc.kidly.boundedcontext.recommend.app.port.size;

import com.bbcc.kidly.boundedcontext.recommend.domain.size.AiSizeAnswer;

/**
 * 규칙 계산 결과를 받아 그 상품의 사이즈 안에서 사이즈를 고르는 나가는 연결의 요청서다.
 * 트랜잭션 밖에서 부른다. 구현은 응답을 받지 못하면 예외를 던지고, 호출하는 쪽이 규칙 결과로 대신한다.
 */
public interface AiSizeChooser {

    AiSizeAnswer choose(SizeAiQuestion question);
}
