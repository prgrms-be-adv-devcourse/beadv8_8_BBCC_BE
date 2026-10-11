package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import java.util.stream.Stream;

/**
 * 모듈러 모놀리스의 경계를 지킨다.
 * 도메인끼리는 서로의 코드를 참조하지 않고, shared의 계약(이벤트)으로만 주고받는다.
 */
@AnalyzeClasses(packages = "com.bbcc.kidly", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String BASE = "com.bbcc.kidly";
    private static final String CONTEXTS = BASE + ".boundedcontext";
    private static final List<String> DOMAINS =
        List.of("member", "product", "recommend", "order", "payment", "settlement");

    @ArchTest
    void domainsDoNotDependOnOtherDomains(JavaClasses classes) {
        for (String domain : DOMAINS) {
            for (String other : DOMAINS) {
                if (domain.equals(other)) {
                    continue;
                }
                noClasses().that().resideInAPackage(domainPackage(domain))
                    .should().dependOnClassesThat().resideInAPackage(domainPackage(other))
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
    static final ArchRule SHARED_DOES_NOT_DEPEND_ON_DOMAINS_OR_GLOBAL = noClasses()
        .that().resideInAPackage(BASE + ".shared..")
        .should().dependOnClassesThat().resideInAnyPackage(Stream.concat(
            DOMAINS.stream().map(ArchitectureTest::domainPackage),
            Stream.of(BASE + ".global..")).toArray(String[]::new))
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule DOMAIN_LAYER_IS_INDEPENDENT = noClasses()
        .that().resideInAPackage(CONTEXTS + ".*.domain..")
        .should().dependOnClassesThat().resideInAnyPackage(
            CONTEXTS + ".*.app..", CONTEXTS + ".*.in..", CONTEXTS + ".*.out..")
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule IN_DOES_NOT_USE_OUT = noClasses()
        .that().resideInAPackage(CONTEXTS + ".*.in..")
        .should().dependOnClassesThat().resideInAPackage(CONTEXTS + ".*.out..")
        .allowEmptyShould(true);

    private static String domainPackage(String domain) {
        return CONTEXTS + "." + domain + "..";
    }
}
