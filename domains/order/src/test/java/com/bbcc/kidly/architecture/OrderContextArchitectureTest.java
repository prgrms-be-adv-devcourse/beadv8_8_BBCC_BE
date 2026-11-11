package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.nameMatching;
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
 * 주문 모듈 안의 컨텍스트(order · cart · delivery, #617 · wish, #640) 사이 규칙. 계층 규칙은 {@link LayerArchitectureTest}가 컨텍스트마다 건다.
 * <ul>
 *   <li>다른 컨텍스트는 자기 out 어댑터만 참조하고, 그 대상은 상대의 app/port/in(들어오는 포트)뿐이다(#641).
 *       app · domain · in은 다른 컨텍스트를 모르고 자기 app/port(나가는 포트)만 쓴다</li>
 *   <li>app/port/in에는 인터페이스 · record만 두고, 자기 port/in 밖(도메인 · 다른 컨텍스트)을 참조하지 않는다</li>
 *   <li>컨텍스트 사이 창구였던 api 패키지는 두지 않는다</li>
 *   <li>조르기(wish)는 아무도 참조하지 않는다. 걷어낼 때 wish 패키지만 지우면 된다</li>
 *   <li>장바구니(cart)는 주문(order) 흐름을 모른다(#998, 순환 금지: 주문 → 장바구니, 조르기 → 장바구니 방향만). 예외는 모듈 공용 기반으로
 *       쓰는 주문 컨텍스트의 복사본 조회(ReplicaLookup) · 이벤트 발행(OrderEventIssuer, 아웃박스) 포트뿐이고, 이 둘은 나중에 공용 컨텍스트로
 *       옮긴다</li>
 *   <li>조르기(wish)는 장바구니(cart)만 안다(#999, 순환 금지: 조르기 → 장바구니 방향만). 장바구니 입구(app/port/in)로 칸을 예약하고
 *       구매 사실은 이벤트로 받는다. 주문 · 배송은 참조하지 않는다. 예외는 장바구니와 같은 모듈 공용 기반(주문 컨텍스트의 복사본 조회
 *       ReplicaLookup · 이벤트 발행 OrderEventIssuer 포트)뿐이다</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.bbcc.kidly.boundedcontext",
    importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class OrderContextArchitectureTest {

    private static final String BASE = "com.bbcc.kidly.boundedcontext.";
    private static final List<String> CONTEXTS = List.of("order", "cart", "delivery", "wish");
    private static final String WISH = "wish";

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
                .as(context + "의 app · domain · in은 " + other + "를 참조하지 않는다")))
        .toList());

    @ArchTest
    static final ArchRule OUT_USES_ONLY_OTHER_IN_PORTS = CompositeArchRule.of(CONTEXTS.stream()
        .flatMap(context -> CONTEXTS.stream().filter(other -> !other.equals(context))
            .map(other -> noClasses()
                .that().resideInAPackage(BASE + context + ".out..")
                .should().dependOnClassesThat(resideInAPackage(BASE + other + "..")
                    .and(not(resideInAPackage(BASE + other + ".app.port.in.."))))
                .as(context + " out은 " + other + "의 app/port/in만 참조한다")))
        .toList());

    @ArchTest
    static final ArchRule NO_API_PACKAGES = noClasses()
        .should().resideInAPackage(BASE + "*.api..")
        .as("컨텍스트 사이 api 패키지는 두지 않는다(#641)");

    @ArchTest
    static final ArchRule NO_CONTEXT_DEPENDS_ON_WISH = noClasses()
        .that().resideInAPackage("com.bbcc.kidly.boundedcontext..")
        .and().resideOutsideOfPackage(BASE + WISH + "..")
        .should().dependOnClassesThat().resideInAPackage(BASE + WISH + "..")
        .as("주문 · 장바구니 · 배송은 조르기를 참조하지 않는다(#640)");

    // 주문 컨텍스트 안의 모듈 공용 기반(복사본 · 아웃박스)을 여는 포트와 그 안의 값(중첩 record)
    private static final String ORDER_SHARED_BASE =
        BASE.replace(".", "\\.") + "order\\.app\\.port\\.in\\.(ReplicaLookup|OrderEventIssuer)(\\$.*)?";

    @ArchTest
    static final ArchRule CART_DOES_NOT_DEPEND_ON_ORDER = noClasses()
        .that().resideInAPackage(BASE + "cart..")
        .should().dependOnClassesThat(resideInAPackage(BASE + "order..").and(not(nameMatching(ORDER_SHARED_BASE))))
        .as("장바구니는 주문을 참조하지 않는다(모듈 공용 기반 복사본 조회 · 이벤트 발행 포트만 예외, #998)");

    @ArchTest
    static final ArchRule WISH_DEPENDS_ONLY_ON_CART = noClasses()
        .that().resideInAPackage(BASE + WISH + "..")
        .should().dependOnClassesThat(resideInAnyPackage(BASE + "order..", BASE + "delivery..")
            .and(not(nameMatching(ORDER_SHARED_BASE))))
        .as("조르기는 주문 · 배송을 참조하지 않는다(장바구니만, 모듈 공용 기반 복사본 조회 · 이벤트 발행 포트만 예외, #999)");

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
