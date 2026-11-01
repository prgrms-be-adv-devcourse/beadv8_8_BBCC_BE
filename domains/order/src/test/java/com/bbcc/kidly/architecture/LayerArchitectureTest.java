package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.List;

/**
 * 이 도메인 모듈 안의 계층 규칙({@link LayerRules})을 검사한다.
 * 클래스패스에는 이 도메인만 있어 boundedcontext 전체를 읽어도 자기 코드만 검사된다.
 */
@AnalyzeClasses(packages = "com.bbcc.kidly.boundedcontext",
    importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class LayerArchitectureTest {

    private static final String CONTEXTS = "com.bbcc.kidly.boundedcontext";

    @ArchTest
    static final ArchTests LAYER_RULES = ArchTests.in(LayerRules.class);

    /**
     * 계층 규칙은 빈 대상을 허용하므로, 주문 모듈의 컨텍스트(#617, 조르기 #640)마다 각 계층 클래스가 이 검사 대상에 실제로 들어 있고
     * 계층 규칙의 패키지 틀({@code boundedcontext.*.계층..})에 걸리는지 확인한다(검사가 조용히 빠지는 것 방지).
     */
    @ArchTest
    static final ArchRule LAYER_RULES_COVER_EVERY_CONTEXT = CompositeArchRule.of(
        List.of("order", "cart", "delivery", "wish").stream()
            .flatMap(context -> List.of("domain", "app", "in", "out").stream()
                .map(layer -> classes()
                    .that().resideInAPackage(CONTEXTS + ".*." + layer + "..")
                    .and().resideInAPackage(CONTEXTS + "." + context + "." + layer + "..")
                    .should().resideInAPackage(CONTEXTS + "." + context + "." + layer + "..")
                    .as(context + "." + layer + " 클래스가 계층 규칙 검사 대상에 있다")))
            .toList());
}
