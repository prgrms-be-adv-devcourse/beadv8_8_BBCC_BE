package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;

/**
 * 커뮤니티 모듈 안의 컨텍스트(review · favorite) 사이 규칙. 계층 규칙은 {@link LayerArchitectureTest}가 건다.
 * <ul>
 *   <li>다른 컨텍스트는 자기 out 어댑터만 참조하고, 그 대상은 상대의 app/port/in(들어오는 포트)뿐이다.
 *       app · domain · in은 다른 컨텍스트를 모르고 자기 app/port(나가는 포트)만 쓴다</li>
 *   <li>app/port/in에는 인터페이스 · record만 두고, 자기 port/in 밖(도메인 · 다른 컨텍스트)을 참조하지 않는다</li>
 *   <li>컨텍스트 사이 창구 api 패키지는 두지 않는다</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.bbcc.kidly.boundedcontext",
    importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class CommunityContextArchitectureTest {

    private static final String BASE = "com.bbcc.kidly.boundedcontext.";
    private static final List<String> CONTEXTS = List.of("review", "favorite");

    @ArchTest
    static final ArchRule EVERY_CONTEXT_HAS_CLASSES = CompositeArchRule.of(CONTEXTS.stream()
        .map(context -> classes().that().resideInAPackage(BASE + context + "..")
            .should().resideInAPackage(BASE + context + "..")
            .as(context + " 컨텍스트 클래스가 있다"))
        .toList());

    @ArchTest
    static final ArchRule ONLY_OUT_KNOWS_OTHER_CONTEXTS = CompositeArchRule.of(CONTEXTS.stream()
        .flatMap(context -> CONTEXTS.stream().filter(other -> !other.equals(context))
            .map(other -> noClasses()
                .that().resideInAPackage(BASE + context + "..")
                .and().resideOutsideOfPackage(BASE + context + ".out..")
                .should().dependOnClassesThat().resideInAPackage(BASE + other + "..")
                .allowEmptyShould(true)
                .as(context + "의 app · domain · in은 " + other + "를 참조하지 않는다")))
        .toList());

    @ArchTest
    static final ArchRule OUT_USES_ONLY_OTHER_IN_PORTS = CompositeArchRule.of(CONTEXTS.stream()
        .flatMap(context -> CONTEXTS.stream().filter(other -> !other.equals(context))
            .map(other -> noClasses()
                .that().resideInAPackage(BASE + context + ".out..")
                .should().dependOnClassesThat(resideInAPackage(BASE + other + "..")
                    .and(not(resideInAPackage(BASE + other + ".app.port.in.."))))
                .allowEmptyShould(true)
                .as(context + " out은 " + other + "의 app/port/in만 참조한다")))
        .toList());

    @ArchTest
    static final ArchRule NO_API_PACKAGES = noClasses()
        .should().resideInAPackage(BASE + "*.api..")
        .as("컨텍스트 사이 api 패키지는 두지 않는다");

    @ArchTest
    static final ArchRule IN_PORTS_USE_ONLY_THEMSELVES = CompositeArchRule.of(CONTEXTS.stream()
        .map(context -> noClasses()
            .that().resideInAPackage(BASE + context + ".app.port.in..")
            .should().dependOnClassesThat(resideInAPackage("com.bbcc.kidly.boundedcontext..")
                .and(not(resideInAPackage(BASE + context + ".app.port.in.."))))
            .allowEmptyShould(true)
            .as(context + " app/port/in은 자기 port/in 밖(도메인 · 다른 컨텍스트 포함)을 참조하지 않는다"))
        .toList());

    @ArchTest
    static final ArchRule IN_PORTS_HAVE_ONLY_INTERFACES_AND_RECORDS = classes()
        .that().resideInAPackage(BASE + "*.app.port.in..")
        .and().doNotHaveSimpleName("package-info")
        .should(beInterfaceOrRecord())
        .allowEmptyShould(true);

    private static ArchCondition<JavaClass> beInterfaceOrRecord() {
        return new ArchCondition<>("인터페이스 또는 record다") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean ok = javaClass.isInterface() || javaClass.isRecord();
                if (!ok) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                        javaClass.getName() + "는 인터페이스 · record가 아니다"));
                }
            }
        };
    }
}
