package com.bbcc.kidly.boundedcontext.recommend.out.ai;

import com.bbcc.kidly.boundedcontext.recommend.app.port.size.AiSizeChooser;
import com.bbcc.kidly.boundedcontext.recommend.app.port.size.SizeAiQuestion;
import com.bbcc.kidly.boundedcontext.recommend.domain.size.AiSizeAnswer;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spring AI 채팅 모델(OpenAI)에 규칙 계산 결과와 상품 사이즈를 보내 사이즈를 고르게 한다.
 * 모델 호출이 실패하면 예외를 그대로 던지고, 호출하는 쪽이 규칙 결과로 대신한다. 질문과 답의 본문은 로그에 남기지 않는다.
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
@RequiredArgsConstructor
public class OpenAiSizeChooser implements AiSizeChooser {

    private static final String SYSTEM_INSTRUCTION = """
        당신은 키즈 패션 쇼핑몰의 사이즈 추천 도우미입니다. 사용자 메시지는 JSON 질문 하나입니다.
        - 질문 JSON의 sizes에 있는 사이즈 이름 중 하나만 recommendedSize로 고르세요. 목록에 없는 사이즈는 쓰지 마세요.
        - ruleResult는 키와 체형 규칙으로 계산한 참고 답입니다. 따르지 않아도 됩니다.
        - child의 키, 몸무게, 입력 체형(bodyType), 판정 체형(judgedBodyType), 선호 핏(preferredFit)과
          sizes의 사이즈별 권장 키, 실측(cm)을 함께 보고 고르세요.
          judgedBodyType이 null이면 체형을 쓰지 않고 키만 보고 고른 것입니다.
        - growthSpeed는 아이의 키 성장 속도(cm/월)입니다. cmPerMonth는 최근 12개월 성장 기록과 나이 · 성별 표 값을 섞은 전체 속도,
          recentCmPerMonth는 최근 3개월 속도(기록이 부족하면 null), childSharePercent는 전체 속도에서 아이 기록이 차지하는 비중(%),
          tableCmPerMonth와 tableSource는 표 값과 그 출처입니다. recordCount는 계산에 쓴 기록 개수,
          recordMonths는 첫 기록과 마지막 기록 사이 개월 수입니다. 최근 속도가 전체 속도보다 빠르면 곧 작아질 수 있으니
          한 치수 크게 볼 수 있고, 느리면 그렇지 않다는 점을 판단에 쓰세요.
          recordMonths와 childSharePercent의 나머지 값(100 − 비중)은 문장에 쓰지 마세요.
          childSharePercent가 0이면 아이 기록 없이 또래 표 값만 쓴 것이니 아이가 실제로 그만큼 자랐다고 쓰지 말고
          또래 기준으로 설명하세요.
        - wearablePeriods는 사이즈마다 앞으로 입을 수 있는 개월 수입니다. lengthMonths는 권장 키 상한까지, widthMonths는
          기준 옷보다 넉넉한 너비를 몸이 채울 때까지, months는 둘 중 짧은 값이고 24가 상한입니다. 계산할 수 없으면 null입니다.
          너무 짧은 사이즈(예: 0 ~ 2개월)는 곧 작아진다는 점을 판단에 쓰세요.
        - 성장 속도나 입을 수 있는 기간을 근거로 쓸 때는 reasons의 type을 GROWTH로 하고, detail에 growthSpeed의 tableSource 값을
          출처로 밝히세요(예: "한국아동패널 기준").
        - fit은 ROOMY, PROPER, TIGHT 중 하나입니다.
        - reasons는 1개에서 3개입니다. type은 HEIGHT, BODY_TYPE, PREFERRED_FIT, MEASUREMENT, RULE_RESULT, GROWTH 중
          하나이고, detail은 보호자에게 그대로 보여 줄 짧은 한국어 존댓말 문장입니다("~요"로 끝냅니다).
          sourceId는 null입니다.
        - referenceFit은 자녀가 지금 잘 맞게 입는 옷(기준 옷)과 이 상품을 비교한 값입니다.
          items의 각 항목에 기준 옷 실측 garmentCm과, 사이즈마다 상품 실측 productCm, 차이 diffCm(상품 - 기준 옷, cm)이 있습니다.
          productPart와 comparedPart가 다르면 같은 종류의 기준 옷이 없어 다른 종류(예: 아우터 상품과 상의 기준 옷)와
          너비 항목만 비교한 것입니다. referenceFit이 null이면 기준 옷이 없는 것이니 무시하세요.
        - ruleResult(키와 체형 규칙 결과)와 referenceFit의 항목별 차이(diffCm)를 함께 보고 사이즈를 고르세요. 몇 cm 넉넉해야 맞는지는 옷 종류와 항목을 보고 판단하세요.
        - itemJudgements는 referenceFit이 있으면 고른 사이즈(recommendedSize)의 값이 referenceFit에 있는 항목마다 하나씩 씁니다.
          code, diffCm(referenceFit에 있는 그 사이즈의 값을 그대로), fit(TIGHT, PROPER, ROOMY 중 하나),
          그 판단에 쓴 여유 범위 allowanceMinCm와 allowanceMaxCm(cm, 최소가 최대보다 크지 않아야 함)을 쓰세요.
          referenceFit이 null이면 빈 목록입니다.
        - reasons의 detail에 숫자를 쓸 때는 질문에 있는 숫자나 itemJudgements의 여유 범위만 쓰고, 새로 계산한 숫자는 쓰지 마세요.
          차이는 부호 없이 "1cm 작아요"처럼 써도 됩니다.
        - MEASUREMENT 근거는 referenceFit이 있으면 기준 옷과의 차이로 설명하고, 없으면 상품 실측을 자녀의 키, 체형, 선호 핏과
          견주어 설명하세요.
        """;

    private final ChatModel chatModel;
    private final JsonMapper jsonMapper;

    @Override
    public AiSizeAnswer choose(SizeAiQuestion question) {
        return ChatClient.create(chatModel).prompt()
            .system(SYSTEM_INSTRUCTION)
            .user(jsonMapper.writeValueAsString(question))
            .call()
            .entity(AiSizeAnswer.class);
    }
}
