package com.bbcc.kidly.boundedcontext.review.app;

import com.bbcc.kidly.global.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 커서 목록의 입력 값: 쪽 크기(size)와 커서. 목록마다 같은 규칙으로 검사한다.
 * <ul>
 *   <li>size: 1~50 정수, 없으면 20. 아니면 INVALID_PAGE_SIZE</li>
 *   <li>커서: 마지막 항목의 정렬 기준 값을 Base64URL로 감싼 불투명 문자열. 시각순 목록은 (정렬 시각, id)(Cursor),
 *       id순 목록은 id만(IdCursor). 읽을 수 없거나 다른 목록의 커서면 INVALID_CURSOR</li>
 * </ul>
 */
final class PageRequestValues {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 50;
    private static final String SEPARATOR = "|";

    private PageRequestValues() {
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
        throw new BusinessException(ReviewErrorCode.INVALID_PAGE_SIZE);
    }

    /**
     * id 순 목록의 커서 = 마지막 항목의 id.
     */
    record IdCursor(Long id) {

        String encode() {
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(id).getBytes(StandardCharsets.UTF_8));
        }

        static IdCursor decode(String cursor) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                return new IdCursor(Long.parseLong(raw));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(ReviewErrorCode.INVALID_CURSOR);
            }
        }
    }

    /**
     * 커서 = 마지막 항목의 정렬 시각과 id.
     */
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
                    throw new BusinessException(ReviewErrorCode.INVALID_CURSOR);
                }
                return new Cursor(LocalDateTime.parse(raw.substring(0, separator)),
                    Long.parseLong(raw.substring(separator + 1)));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw new BusinessException(ReviewErrorCode.INVALID_CURSOR);
            }
        }
    }
}
