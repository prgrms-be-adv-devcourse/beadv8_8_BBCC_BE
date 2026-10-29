package com.bbcc.kidly.boundedcontext.search.out;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 검색 색인 이름 설정. 값이 비었거나 형식이 맞지 않으면 기동에 실패한다.
 *
 * @param prefix 색인 이름 맨 앞에 붙이는 환경 이름(예: local-products). dev와 prod가 Elasticsearch 하나를
 *     색인 이름으로만 나눠 쓸 수 있게 하고, 색인 이름에 쓸 수 없는 글자가 들어가지 않게 소문자만 받는다
 */
@Validated
@ConfigurationProperties("kidly.search.index")
public record SearchIndexProperties(@NotBlank @Pattern(regexp = "[a-z]+") String prefix) {

    public String productIndex() {
        return prefix + "-products";
    }

    public String sellerIndex() {
        return prefix + "-sellers";
    }
}
