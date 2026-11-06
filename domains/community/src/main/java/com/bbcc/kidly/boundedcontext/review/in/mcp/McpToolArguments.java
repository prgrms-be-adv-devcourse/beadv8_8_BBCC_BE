package com.bbcc.kidly.boundedcontext.review.in.mcp;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 도구 인자(JSON 객체) 해석. 틀리면 McpToolArgumentException(정해진 문구, 받은 값은 넣지 않는다).
 * 정수 인자는 소수부 없는 JSON 숫자나 숫자만 든 문자열을 받는다.
 */
public final class McpToolArguments {

    private static final Pattern DIGITS = Pattern.compile("-?[0-9]{1,18}");

    private final Map<String, Object> values;

    public McpToolArguments(Map<String, Object> values) {
        this.values = values == null ? Map.of() : values;
    }

    /**
     * 문자열 인자(필수, 공백만이면 없는 것으로 본다).
     */
    public String requiredText(String name) {
        return optionalText(name).filter(value -> !value.isBlank())
            .orElseThrow(() -> new McpToolArgumentException(name + "가 필요합니다"));
    }

    public Optional<String> optionalText(String name) {
        Object value = values.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (value instanceof String text) {
            return Optional.of(text);
        }
        throw new McpToolArgumentException(name + "는 문자열로 보내 주세요");
    }

    /**
     * ID 인자를 문자열로(필수). 숫자 · 문자열 모두 받고, 값 확인은 받는 app 서비스가 한다.
     */
    public String requiredIdText(String name) {
        Object value = values.get(name);
        if (value == null) {
            throw new McpToolArgumentException(name + "가 필요합니다");
        }
        if (value instanceof String text) {
            return text;
        }
        return String.valueOf(integral(name, value));
    }

    public long requiredPositiveLong(String name) {
        Object value = values.get(name);
        if (value == null) {
            throw new McpToolArgumentException(name + "가 필요합니다");
        }
        long number = integral(name, value);
        if (number < 1) {
            throw new McpToolArgumentException(name + "는 양의 정수로 보내 주세요");
        }
        return number;
    }

    public int optionalNonNegativeInt(String name, int defaultValue) {
        Object value = values.get(name);
        if (value == null) {
            return defaultValue;
        }
        long number = integral(name, value);
        if (number < 0 || number > Integer.MAX_VALUE) {
            throw new McpToolArgumentException(name + "는 0 이상 정수로 보내 주세요");
        }
        return (int) number;
    }

    public int optionalPositiveInt(String name, int defaultValue) {
        Object value = values.get(name);
        if (value == null) {
            return defaultValue;
        }
        long number = integral(name, value);
        if (number < 1) {
            throw new McpToolArgumentException(name + "는 1 이상 정수로 보내 주세요");
        }
        return (int) Math.min(number, Integer.MAX_VALUE);
    }

    private static long integral(String name, Object value) {
        try {
            if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
                return ((Number) value).longValue();
            }
            if (value instanceof BigInteger big) {
                return big.longValueExact();
            }
            if (value instanceof BigDecimal decimal) {
                return decimal.longValueExact();
            }
            if (value instanceof Double || value instanceof Float) {
                return new BigDecimal(value.toString()).longValueExact();
            }
            if (value instanceof String text && DIGITS.matcher(text).matches()) {
                return Long.parseLong(text);
            }
        } catch (ArithmeticException | NumberFormatException e) {
            // 아래에서 같은 오류로 응답한다
        }
        throw new McpToolArgumentException(name + "는 정수로 보내 주세요");
    }
}
