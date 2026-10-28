package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.bbcc.kidly.global.eventpublisher.DomainEventListener;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 모듈 안의 계층 규칙(로우레벨 설계 3절).
 * 모듈 사이 규칙과 in → out 금지는 {@link ArchitectureTest}가 검사한다.
 *
 * <pre>
 * in ──▶ app ──▶ domain ◀── out
 *         └──▶ app/port ◀── out (포트 구현)
 * </pre>
 */
@AnalyzeClasses(packages = "com.bbcc.kidly",
    importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class OrderArchitectureTest {

    private static final String ORDER = "com.bbcc.kidly.boundedcontext.order";
    private static final String DOMAIN = ORDER + ".domain..";
    private static final String APP = ORDER + ".app..";
    private static final String PORT = ORDER + ".app.port..";
    private static final String IN = ORDER + ".in..";
    private static final String EVENT = ORDER + ".in.event..";
    private static final String OUT = ORDER + ".out..";
    private static final String SHARED_PRODUCT = "com.bbcc.kidly.shared.product..";
    private static final String SHARED_CHILDPROFILE = "com.bbcc.kidly.shared.childprofile..";

    /**
     * domain은 jakarta.persistence까지만 안다.
     * 예외: 팀 공통 ErrorCode 계약이 HttpStatus를 요구하므로 OrderErrorCode의 HttpStatus만 허용한다(결정필요 E23).
     */
    @ArchTest
    static final ArchRule DOMAIN_DOES_NOT_USE_SPRING = noClasses()
        .that().resideInAPackage(DOMAIN)
        .should().dependOnClassesThat(
            resideInAPackage("org.springframework..").and(not(equivalentTo(HttpStatus.class))))
        .allowEmptyShould(true);

    /**
     * domain에는 인터페이스를 두지 않는다(엔티티 · record · enum만). 저장소 등 나가는 인터페이스는 app/port에 둔다.
     */
    @ArchTest
    static final ArchRule DOMAIN_HAS_NO_INTERFACES = noClasses()
        .that().resideInAPackage(DOMAIN)
        .and().doNotHaveSimpleName("package-info")
        .should().beInterfaces()
        .allowEmptyShould(true);

    /**
     * 애그리거트 패키지(domain.order · cart · delivery · replica)끼리는 의존하지 않고, domain 바로 아래 공통 값에만 의존한다.
     */
    @ArchTest
    static final ArchRule DOMAIN_AGGREGATES_DO_NOT_DEPEND_ON_EACH_OTHER = slices()
        .matching(ORDER + ".domain.(*)..")
        .should().notDependOnEachOther()
        .allowEmptyShould(true);

    /**
     * 컨트롤러는 in.web 아래에만 둔다.
     */
    @ArchTest
    static final ArchRule CONTROLLERS_LIVE_IN_WEB = classes()
        .that().resideInAPackage(ORDER + "..")
        .and().areAnnotatedWith(RestController.class)
        .should().resideInAPackage(ORDER + ".in.web..")
        .allowEmptyShould(true);

    /**
     * 이벤트 리스너 메서드는 in.event 아래에만 둔다.
     */
    @ArchTest
    static final ArchRule EVENT_LISTENERS_LIVE_IN_EVENT = methods()
        .that().areAnnotatedWith(DomainEventListener.class)
        .and().areDeclaredInClassesThat().resideInAPackage(ORDER + "..")
        .should().beDeclaredInClassesThat().resideInAPackage(EVENT)
        .allowEmptyShould(true);

    /**
     * 다른 도메인 이벤트(상품 · 자녀)를 아는 클래스는 in.event 리스너뿐이다. 이벤트 → 커맨드 변환은 리스너 안에서만 한다.
     */
    @ArchTest
    static final ArchRule ONLY_REPLICA_LISTENERS_KNOW_OTHER_DOMAIN_EVENTS = noClasses()
        .that().resideInAPackage(ORDER + "..")
        .and().resideOutsideOfPackage(EVENT)
        .should().dependOnClassesThat().resideInAnyPackage(SHARED_PRODUCT, SHARED_CHILDPROFILE)
        .allowEmptyShould(true);

    /**
     * domain · app은 다른 도메인 계약(상품 · 자녀)을 쓰지 않는다. 다른 도메인 enum은 name() 문자열로 받는다.
     */
    @ArchTest
    static final ArchRule ORDER_DOMAIN_DOES_NOT_USE_OTHER_DOMAIN_CONTRACTS = noClasses()
        .that().resideInAnyPackage(DOMAIN, APP)
        .should().dependOnClassesThat().resideInAnyPackage(SHARED_PRODUCT, SHARED_CHILDPROFILE)
        .allowEmptyShould(true);

    /**
     * 스케줄러 메서드는 in.scheduler 아래에만 둔다.
     */
    @ArchTest
    static final ArchRule SCHEDULERS_LIVE_IN_SCHEDULER = methods()
        .that().areAnnotatedWith(Scheduled.class)
        .and().areDeclaredInClassesThat().resideInAPackage(ORDER + "..")
        .should().beDeclaredInClassesThat().resideInAPackage(ORDER + ".in.scheduler..")
        .allowEmptyShould(true);

    /**
     * 기능 패키지(app.cart · replica …)끼리는 의존하지 않는다. 출력 포트(app.port)는 기능이 아니라 모든 기능이 쓴다.
     */
    @ArchTest
    static final ArchRule APP_FEATURES_DO_NOT_DEPEND_ON_EACH_OTHER = slices()
        .matching(ORDER + ".app.(*)..")
        .should().notDependOnEachOther()
        .ignoreDependency(alwaysTrue(), resideInAPackage(PORT))
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule APP_DOES_NOT_USE_IN_OR_OUT = noClasses()
        .that().resideInAPackage(APP)
        .should().dependOnClassesThat().resideInAnyPackage(IN, OUT)
        .allowEmptyShould(true);

    /**
     * app은 JPA 구현을 모른다. 저장은 app/port의 리포지토리 인터페이스로만 한다.
     */
    @ArchTest
    static final ArchRule APP_DOES_NOT_USE_JPA_IMPLEMENTATION = noClasses()
        .that().resideInAPackage(APP)
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework.data.jpa..", "org.hibernate..")
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule OUT_DOES_NOT_USE_IN = noClasses()
        .that().resideInAPackage(OUT)
        .should().dependOnClassesThat().resideInAPackage(IN)
        .allowEmptyShould(true);

    /**
     * 출력 포트만 인터페이스로 둔다. 입력 포트 · 서비스 인터페이스는 만들지 않는다(4.1 · 4.3).
     */
    @ArchTest
    static final ArchRule PORTS_ARE_INTERFACES = classes()
        .that().resideInAPackage(PORT)
        .and().doNotHaveSimpleName("package-info")
        .should().beInterfaces()
        .allowEmptyShould(true);

    @ArchTest
    static final ArchRule APP_INTERFACES_LIVE_IN_PORT = classes()
        .that().resideInAPackage(APP)
        .and().areInterfaces()
        .and().areNotAnnotations()
        .and().doNotHaveSimpleName("package-info")
        .should().resideInAPackage(PORT)
        .allowEmptyShould(true);
}
