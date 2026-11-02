package com.bbcc.kidly.boundedcontext.review.app;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 저장 실패가 특정 유일 제약 위반(SQLState 23505 + 제약 이름)인지 본다. 그 위반만 "이미 있음" 후보로 보고, 그 밖의 위반은 부르는 쪽이
 * 그대로 던진다.
 */
final class UniqueViolations {

    private static final String UNIQUE_VIOLATION = "23505";

    private UniqueViolations() {
    }

    static boolean isViolationOf(DataIntegrityViolationException e, String constraint) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return UNIQUE_VIOLATION.equals(violation.getSQLState())
                    && constraint.equalsIgnoreCase(violation.getConstraintName());
            }
        }
        return false;
    }
}
