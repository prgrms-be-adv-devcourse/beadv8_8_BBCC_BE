package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 도메인 안의 계층 규칙. 아래 방향으로만 참조할 수 있다.
 *
 * <pre>
 * in  → app → domain
 * out → app.port · domain
 * </pre>
 *
 * 도메인 모듈마다 자기 클래스를 검사한다: {@code @ArchTest static final ArchTests LAYER_RULES = ArchTests.in(LayerRules.class);}
 * 도메인 사이 참조는 Gradle 의존으로 막는다(kidly.domain).
 */
public final class LayerRules {

    private static final String CONTEXTS = "com.bbcc.kidly.boundedcontext";

    /**
     * 검사할 도메인 클래스가 있어야 한다. 아래 계층 규칙은 계층이 없는 도메인을 위해 빈 대상을 허용하므로,
     * 도메인 코드가 통째로 빠져 검사가 조용히 생략되는 것을 이 규칙이 막는다.
     */
    @ArchTest
    static final ArchRule DOMAIN_CLASSES_EXIST = classes()
        .that().resideInAPackage(CONTEXTS + "..")
        .should().resideInAPackage(CONTEXTS + "..")
        .as("검사할 도메인 클래스가 있다");

    @ArchTest
    static final ArchRule DOMAIN_LAYER_IS_INDEPENDENT = noClasses()
        .that().resideInAPackage(CONTEXTS + ".*.domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            CONTEXTS + ".*.app..", CONTEXTS + ".*.in..", CONTEXTS + ".*.out..")
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule IN_DOES_NOT_USE_DOMAIN = noClasses()
        .that().resideInAPackage(CONTEXTS + ".*.in..")
        .should().dependOnClassesThat().resideInAPackage(CONTEXTS + ".*.domain..")
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule IN_DOES_NOT_USE_OUT = noClasses()
        .that().resideInAPackage(CONTEXTS + ".*.in..")
        .should().dependOnClassesThat().resideInAPackage(CONTEXTS + ".*.out..")
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule APP_DOES_NOT_USE_IN_OR_OUT = noClasses()
        .that().resideInAPackage(CONTEXTS + ".*.app..")
        .should().dependOnClassesThat().resideInAnyPackage(CONTEXTS + ".*.in..", CONTEXTS + ".*.out..")
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule OUT_DOES_NOT_USE_IN = noClasses()
        .that().resideInAPackage(CONTEXTS + ".*.out..")
        .should().dependOnClassesThat().resideInAPackage(CONTEXTS + ".*.in..")
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule OUT_USES_ONLY_PORT_OF_APP = noClasses()
        .that().resideInAPackage(CONTEXTS + ".*.out..")
        .should().dependOnClassesThat(resideInAPackage(CONTEXTS + ".*.app..")
            .and(resideOutsideOfPackage(CONTEXTS + ".*.app.port..")))
        .allowEmptyShould(true);

    private LayerRules() {
    }
}
