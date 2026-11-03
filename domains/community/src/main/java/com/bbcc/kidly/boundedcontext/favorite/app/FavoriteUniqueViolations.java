package com.bbcc.kidly.boundedcontext.favorite.app;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 저장 실패가 특정 유일 제약 위반(SQLState 23505 + 제약 이름)인지 본다.
 */
final class FavoriteUniqueViolations {

    private static final String UNIQUE_VIOLATION = "23505";

    private FavoriteUniqueViolations() {
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
