package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;

/**
 * 모듈러 모놀리스의 경계를 지킨다.
 * 다른 도메인은 그 도메인의 api 패키지(공개 인터페이스, DTO, 이벤트)만 참조할 수 있다.
 */
@AnalyzeClasses(packages = "com.bbcc.kidly", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.bbcc.kidly";
    private static final List<String> DOMAINS =
        List.of("member", "product", "recommend", "order", "payment", "settlement");

    @ArchTest
    void domainsDependOnlyOnOtherDomainsApi(JavaClasses classes) {
        for (String domain : DOMAINS) {
            for (String other : DOMAINS) {
                if (domain.equals(other)) {
                    continue;
                }
                noClasses().that().resideInAPackage(domainPackage(domain))
                    .should().dependOnClassesThat(resideInAPackage(domainPackage(other))
                        .and(not(resideInAPackage(BASE + "." + other + ".api.."))))
                    .allowEmptyShould(true)
                    .check(classes);
            }
        }
    }

    @ArchTest
    static final ArchRule GLOBAL_DOES_NOT_DEPEND_ON_DOMAINS = noClasses()
        .that().resideInAPackage(BASE + ".global..")
        .should().dependOnClassesThat().resideInAnyPackage(
            DOMAINS.stream().map(ArchitectureTest::domainPackage).toArray(String[]::new))
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule DOMAIN_LAYER_IS_INDEPENDENT = noClasses()
        .that().resideInAPackage(BASE + ".*.domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            BASE + ".*.presentation..", BASE + ".*.application..", BASE + ".*.infrastructure..")
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule PRESENTATION_DOES_NOT_USE_INFRASTRUCTURE = noClasses()
        .that().resideInAPackage(BASE + ".*.presentation..")
        .should().dependOnClassesThat().resideInAPackage(BASE + ".*.infrastructure..")
        .allowEmptyShould(true);

    private static String domainPackage(String domain) {
        return BASE + "." + domain + "..";
    }
}
