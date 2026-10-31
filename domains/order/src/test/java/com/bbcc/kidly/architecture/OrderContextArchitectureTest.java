package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.List;

/**
 * 주문 모듈 안의 컨텍스트(order · cart · delivery) 사이 규칙(#617). 계층 규칙은 {@link LayerArchitectureTest}가 컨텍스트마다 건다.
 * <ul>
 *   <li>한 컨텍스트는 다른 컨텍스트의 api(인터페이스 · record)만 참조한다</li>
 *   <li>api끼리는 서로 참조하지 않고, api에는 인터페이스 · record만 둔다(구현은 그 컨텍스트 안)</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.bbcc.kidly.boundedcontext",
    importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class OrderContextArchitectureTest {

    private static final String BASE = "com.bbcc.kidly.boundedcontext.";
    private static final List<String> CONTEXTS = List.of("order", "cart", "delivery");

    @ArchTest
    static final ArchRule EVERY_CONTEXT_HAS_CLASSES = CompositeArchRule.of(CONTEXTS.stream()
        .map(context -> classes().that().resideInAPackage(BASE + context + "..")
            .should().resideInAPackage(BASE + context + "..")
            .as(context + " 컨텍스트 클래스가 있다"))
        .toList());

    @ArchTest
    static final ArchRule CONTEXTS_USE_ONLY_OTHER_APIS = CompositeArchRule.of(CONTEXTS.stream()
        .flatMap(context -> CONTEXTS.stream().filter(other -> !other.equals(context))
            .map(other -> noClasses()
                .that().resideInAPackage(BASE + context + "..")
                .should().dependOnClassesThat(resideInAPackage(BASE + other + "..")
                    .and(resideOutsideOfPackage(BASE + other + ".api..")))
                .as(context + "는 " + other + "의 api만 참조한다")))
        .toList());

    @ArchTest
    static final ArchRule APIS_DO_NOT_DEPEND_ON_EACH_OTHER = CompositeArchRule.of(CONTEXTS.stream()
        .map(context -> noClasses()
            .that().resideInAPackage(BASE + context + ".api..")
            .should().dependOnClassesThat(resideInAPackage("com.bbcc.kidly.boundedcontext..")
                .and(resideOutsideOfPackage(BASE + context + ".api..")))
            .allowEmptyShould(true)
            .as(context + " api는 다른 패키지(다른 api 포함)를 참조하지 않는다"))
        .toList());

    @ArchTest
    static final ArchRule APIS_HAVE_ONLY_INTERFACES_AND_RECORDS = classes()
        .that().resideInAPackage(BASE + "*.api..")
        .and().doNotHaveSimpleName("package-info")
        .should(beInterfaceOrRecord())
        .allowEmptyShould(true);

    private static com.tngtech.archunit.lang.ArchCondition<JavaClass> beInterfaceOrRecord() {
        return new com.tngtech.archunit.lang.ArchCondition<>("인터페이스 또는 record다") {
            @Override
            public void check(JavaClass javaClass, com.tngtech.archunit.lang.ConditionEvents events) {
                boolean ok = javaClass.isInterface() || javaClass.isRecord();
                if (!ok) {
                    events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(javaClass,
                        javaClass.getName() + "는 인터페이스 · record가 아니다"));
                }
            }
        };
    }
}
