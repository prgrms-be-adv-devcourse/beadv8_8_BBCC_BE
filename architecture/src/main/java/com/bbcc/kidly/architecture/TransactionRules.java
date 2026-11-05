package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 트랜잭션이 끝나면 DB 연결을 돌려주는 Hibernate 설정(서비스 application.yml) 아래에서 실행할 때 실패하는 코드를 빌드에서 막는다.
 * 이 설정에서는 Spring이 트랜잭션을 시작할 때 DB 연결을 미리 준비하지 못해, 격리 수준을 지정한 트랜잭션을 거부한다.
 * 이 모듈은 Spring에 의존하지 않으므로 Spring 타입은 이름으로 찾는다.
 */
public final class TransactionRules {

    private static final String TRANSACTIONAL = "org.springframework.transaction.annotation.Transactional";

    @ArchTest
    static final ArchRule NO_ISOLATION_ON_TRANSACTIONAL_METHODS = noMethods()
        .should().beAnnotatedWith(isolationSpecified())
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule NO_ISOLATION_ON_TRANSACTIONAL_CLASSES = noClasses()
        .should().beAnnotatedWith(isolationSpecified())
        .allowEmptyShould(true);

    // TransactionTemplate처럼 트랜잭션 정의를 코드로 만들 때도 격리 수준을 정하지 않는다
    @ArchTest
    static final ArchRule NO_ISOLATION_LEVEL_IN_CODE = noClasses()
        .should().callMethodWhere(target(name("setIsolationLevel").or(name("setIsolationLevelName")))
            .and(target(owner(assignableTo("org.springframework.transaction.support.DefaultTransactionDefinition")))))
        .allowEmptyShould(true);

    // 트랜잭션 정의를 직접 구현하면 getIsolationLevel()을 재정의해 위 규칙을 거치지 않고 격리 수준을 정할 수 있다
    @ArchTest
    static final ArchRule NO_CUSTOM_TRANSACTION_DEFINITION = noClasses()
        .should().beAssignableTo("org.springframework.transaction.TransactionDefinition")
        .allowEmptyShould(true);

    // 격리 수준을 DEFAULT가 아닌 값으로 지정한 Spring @Transactional이다
    private static DescribedPredicate<JavaAnnotation<?>> isolationSpecified() {
        return DescribedPredicate.describe("격리 수준을 지정한 @Transactional", annotation ->
            annotation.getRawType().getName().equals(TRANSACTIONAL)
                && annotation.get("isolation")
                    .map(value -> !((JavaEnumConstant) value).name().equals("DEFAULT"))
                    .orElse(false));
    }

    private TransactionRules() {
    }
}
