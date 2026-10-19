package com.bbcc.kidly.global.filter;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 요청 로그에 남길 헤더 · 쿼리 · JSON 본문에서 비밀값과 연락처 · 주소를 가린다. 이름 · ID · 금액은 남긴다.
 * 로그 한 줄이 Docker 줄 한도(16KB)를 넘지 않게 긴 값은 자른다.
 */
final class LogMasker {

    static final String MASK = "***";

    private static final Set<String> SECRET_HEADERS = Stream.of(
            HttpHeaders.AUTHORIZATION, HttpHeaders.PROXY_AUTHORIZATION, HttpHeaders.COOKIE, HttpHeaders.SET_COOKIE)
        .map(name -> name.toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
    // 키 이름을 소문자로 바꾸고 _ · - 를 뺀 뒤 이 말이 들어 있으면 가린다 (accessToken, recipient_phone, detailAddress 등)
    // OAuth 인가 코드(code · state)는 /api/v1/auth/** 가 본문을 남기지 않는 경로(RequestLoggingFilter)라 키로 가리지 않는다.
    // 인가 코드가 다른 경로로 들어오게 되면 다시 볼 것
    // uploadurl: S3 presigned URL(서명이 들어 있어 만료 전까지 누구나 올릴 수 있다)
    private static final List<String> SENSITIVE_KEY_PARTS = List.of("password", "token", "secret", "apikey",
        "paymentkey", "uploadurl", "registrationnumber", "phone", "email", "address", "zipcode");
    // 자녀 프로필의 생년월일 · 키 · 몸무게(이름은 남긴다). 상품 치수(minHeightCm 등)는 남기도록 이름이 같을 때만 가린다
    private static final Set<String> SENSITIVE_KEYS = Set.of("birthdate", "heightcm", "weightkg");
    private static final int HEADER_MAX_LENGTH = 300;
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private LogMasker() {
    }

    /**
     * 헤더 이름을 소문자로 바꾸고, 값이 여럿이면 ", "로 잇는다.
     * 인증 · 쿠키 헤더와 이름에 가릴 말이 든 헤더(x-auth-token, x-api-key 등)는 값을 가리고 나머지는 300자까지 남긴다.
     */
    static Map<String, String> headers(Collection<String> names, Function<String, Collection<String>> values) {
        var headers = new TreeMap<String, String>();
        for (var name : names) {
            var lowerName = name.toLowerCase(Locale.ROOT);
            headers.put(lowerName, SECRET_HEADERS.contains(lowerName) || isSensitiveKey(lowerName)
                ? MASK
                : truncate(String.join(", ", values.apply(name)), HEADER_MAX_LENGTH));
        }
        return headers;
    }

    /**
     * 가릴 파라미터의 값만 바꾸고 나머지는 받은 그대로(인코딩 포함) 둔다. 이름을 디코드하지 못하는 파라미터도 가린다.
     */
    static String query(String query) {
        return Arrays.stream(query.split("&"))
            .map(param -> {
                var name = param.split("=", 2)[0];
                return isSensitiveQueryName(name) ? name + "=" + MASK : param;
            })
            .collect(Collectors.joining("&"));
    }

    private static boolean isSensitiveQueryName(String name) {
        try {
            return isSensitiveKey(URLDecoder.decode(name, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * 가릴 키의 값을 중첩 객체 · 배열 안까지 바꿔 한 줄 JSON으로 돌려준다.
     *
     * @throws JacksonException JSON이 아니거나 중간에 끊긴 경우
     */
    static String json(String json) {
        var root = JSON_MAPPER.readTree(json);
        mask(root);
        return JSON_MAPPER.writeValueAsString(root);
    }

    private static void mask(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (var name : List.copyOf(object.propertyNames())) {
                if (isSensitiveKey(name)) {
                    object.put(name, MASK);
                } else {
                    mask(object.get(name));
                }
            }
        } else if (node.isArray()) {
            node.values().forEach(LogMasker::mask);
        }
    }

    private static boolean isSensitiveKey(String key) {
        var normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
        return SENSITIVE_KEYS.contains(normalized) || SENSITIVE_KEY_PARTS.stream().anyMatch(normalized::contains);
    }

    static String truncate(String text, int maxLength) {
        return text.length() <= maxLength
            ? text
            : text.substring(0, maxLength) + "…(잘림, 전체 " + text.length() + "자)";
    }

    /**
     * UTF-8로 maxBytes 바이트까지 남긴다. 한 글자의 바이트를 가르지 않도록 글자 경계에서 자른다.
     */
    static String truncateUtf8(String text, int maxBytes) {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        var end = maxBytes;
        // 10xxxxxx 는 앞 글자에 이어지는 바이트다. 글자의 첫 바이트가 나올 때까지 물러난다
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8) + "…(잘림, 전체 " + bytes.length + "바이트)";
    }
}
