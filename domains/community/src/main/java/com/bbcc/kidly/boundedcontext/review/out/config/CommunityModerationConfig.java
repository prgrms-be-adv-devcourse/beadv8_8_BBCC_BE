package com.bbcc.kidly.boundedcontext.review.out.config;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator;
import com.bbcc.kidly.boundedcontext.review.out.moderation.BannedWordsModerator;
import com.bbcc.kidly.boundedcontext.review.out.moderation.ChainedContentModerator;
import com.bbcc.kidly.boundedcontext.review.out.moderation.ModerationAdapter;
import com.bbcc.kidly.boundedcontext.review.out.moderation.OpenAiCompatibleModerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 리뷰 검사기를 만들고 설정(moderators) 순서대로 이어 붙인다. 쓸 수 없는 검사기(키 없는 AI)는 빼고 기동 때 한 줄 남긴다.
 * 모르는 이름이거나 쓸 수 있는 검사기가 하나도 남지 않으면 그 키 이름이 나오는 오류로 기동에 실패한다.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(CommunityModerationProperties.class)
public class CommunityModerationConfig {

    static final String MODERATORS_KEY = "kidly.community.moderation.moderators";

    @Bean
    ModerationAdapter communityBannedWordsModerator(CommunityModerationProperties properties) {
        return new BannedWordsModerator(properties.bannedWords());
    }

    @Bean
    ModerationAdapter communityAiModerator(CommunityModerationProperties properties) {
        CommunityModerationProperties.Ai ai = properties.ai();
        return new OpenAiCompatibleModerator(ai.baseUrl(), ai.model(), ai.apiKey(), ai.timeout());
    }

    @Bean
    ContentModerator communityContentModerator(List<ModerationAdapter> adapters,
        CommunityModerationProperties properties) {
        Map<String, ModerationAdapter> byName = adapters.stream()
            .collect(Collectors.toMap(ModerationAdapter::name, Function.identity()));
        List<ModerationAdapter> chain = new ArrayList<>();
        for (String name : properties.moderators()) {
            ModerationAdapter adapter = byName.get(name);
            if (adapter == null) {
                throw new IllegalStateException(MODERATORS_KEY + "에 모르는 검사기 이름이 있습니다: " + name
                    + " (쓸 수 있는 이름: " + String.join(", ", byName.keySet().stream().sorted().toList()) + ")");
            }
            if (adapter.available()) {
                chain.add(adapter);
            } else {
                log.info(adapter.unavailableNotice());
            }
        }
        // 쓸 검사기가 하나도 없으면 모든 리뷰가 검사 없이 통과하므로 기동하지 않는다(예: [ai]만 두고 키가 없음)
        if (chain.isEmpty()) {
            throw new IllegalStateException(MODERATORS_KEY
                + "에 쓸 수 있는 검사기가 없습니다. 키가 없는 ai만 있으면 banned-words를 더해 주세요");
        }
        return new ChainedContentModerator(chain);
    }
}
