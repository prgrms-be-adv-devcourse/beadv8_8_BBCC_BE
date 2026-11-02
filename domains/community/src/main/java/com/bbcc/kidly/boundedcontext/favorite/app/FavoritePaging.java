package com.bbcc.kidly.boundedcontext.favorite.app;

import com.bbcc.kidly.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 찜 목록의 쪽 크기(size)와 커서. size는 1~50 정수, 없으면 20. 커서는 마지막 항목의 (찜 시각, id)를 Base64URL로 감싼 불투명 문자열.
 */
final class FavoritePaging {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    private static final String SEPARATOR = "|";

    private FavoritePaging() {
    }

    static int pageSize(String size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        try {
            int value = Integer.parseInt(size);
            if (value >= 1 && value <= MAX_SIZE) {
                return value;
            }
        } catch (NumberFormatException e) {
            // 아래에서 같은 오류로 응답한다
        }
        throw new BusinessException(FavoriteErrorCode.INVALID_PAGE_SIZE);
    }

    record Cursor(LocalDateTime at, Long id) {

        String encode() {
            String raw = at + SEPARATOR + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String cursor) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int separator = raw.indexOf(SEPARATOR);
                if (separator < 0) {
                    throw new BusinessException(FavoriteErrorCode.INVALID_CURSOR);
                }
                return new Cursor(LocalDateTime.parse(raw.substring(0, separator)),
                    Long.parseLong(raw.substring(separator + 1)));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new BusinessException(FavoriteErrorCode.INVALID_CURSOR);
            }
        }
    }
}
