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

@AnalyzeClasses(packages = "com.bbcc.kidly",
    importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class ProductArchitectureTest {

    private static final String DOMAIN = "com.bbcc.kidly.boundedcontext.product.domain..";

    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_USE_SPRING = noClasses()
        .that().resideInAPackage(DOMAIN)
        .should().dependOnClassesThat(
            resideInAPackage("org.springframework..").and(not(equivalentTo(HttpStatus.class))))
        .allowEmptyShould(true);
}
