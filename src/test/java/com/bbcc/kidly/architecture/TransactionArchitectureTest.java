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
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * 트랜잭션이 끝나면 DB 연결을 돌려주는 Hibernate 설정(application.yml) 아래에서 실행할 때 실패하는 코드를 빌드에서 막는다.
 * 이 설정에서는 Spring이 트랜잭션을 시작할 때 DB 연결을 미리 준비하지 못해, 격리 수준을 지정한 트랜잭션을 거부한다.
 */
@AnalyzeClasses(packages = "com.bbcc.kidly", importOptions = ImportOption.DoNotIncludeTests.class)
class TransactionArchitectureTest {

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
            .and(target(owner(assignableTo(DefaultTransactionDefinition.class)))))
        .allowEmptyShould(true);

    // 격리 수준을 DEFAULT가 아닌 값으로 지정한 Spring @Transactional이다
    private static DescribedPredicate<JavaAnnotation<?>> isolationSpecified() {
        return DescribedPredicate.describe("격리 수준을 지정한 @Transactional", annotation ->
            annotation.getRawType().isEquivalentTo(Transactional.class)
                && annotation.get("isolation")
                    .map(value -> !((JavaEnumConstant) value).name().equals(Isolation.DEFAULT.name()))
                    .orElse(false));
    }
}
