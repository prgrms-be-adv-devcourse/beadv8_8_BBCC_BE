package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.http.HttpStatus;

/**
 * 상품 모듈 안의 계층 규칙.
 * 모듈 사이 규칙과 계층 의존 방향은 {@link ArchitectureTest}가 검사한다.
 */
@AnalyzeClasses(packages = "com.bbcc.kidly",
    importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class ProductArchitectureTest {

    private static final String DOMAIN = "com.bbcc.kidly.boundedcontext.product.domain..";

    /**
     * domain은 Spring을 참조하지 않는다.
     * 예외: 팀 공통 ErrorCode 계약이 HttpStatus를 요구하므로 HttpStatus만 허용한다.
     */
    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_USE_SPRING = noClasses()
        .that().resideInAPackage(DOMAIN)
        .should().dependOnClassesThat(
            resideInAPackage("org.springframework..").and(not(equivalentTo(HttpStatus.class))))
        .allowEmptyShould(true);
}
