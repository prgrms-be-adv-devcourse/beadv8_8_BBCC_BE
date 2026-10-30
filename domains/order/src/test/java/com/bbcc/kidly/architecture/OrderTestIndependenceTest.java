package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.order.OrderTestApplication;
import com.bbcc.kidly.boundedcontext.order.domain.replica.ReplicaFixture;
import com.bbcc.kidly.boundedcontext.order.in.event.OrderKafkaListenerTestGuard;
import com.bbcc.kidly.boundedcontext.order.support.IntegrationTest;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주문 테스트는 다른 도메인에 의존하지 않는다(#616). 주문 모듈의 코드 전체(main · 테스트 · testFixtures)를 읽어
 * 다른 도메인 패키지 · 앱 모듈(루트 패키지 · 앱 공통 테스트 기반 support)을 참조하지 않는지 본다. 주문은 주문 · shared · common(global)과
 * 라이브러리만 쓴다. 다른 도메인 모듈은 클래스패스에 없어(kidly.domain) 지금은 컴파일부터 안 되지만, 그 조건이 바뀌어도
 * 이 규칙이 남도록 고정한다.
 */
class OrderTestIndependenceTest {

    private static final String ORDER = "com.bbcc.kidly.boundedcontext.order";

    private static final JavaClasses ORDER_CODE = new ClassFileImporter().importPackages(ORDER);

    private static final ArchRule NO_OTHER_DOMAIN_OR_APP = noClasses()
        .should().dependOnClassesThat(resideInAPackage("com.bbcc.kidly.boundedcontext..")
            .and(not(resideInAPackage(ORDER + ".."))))
        .orShould().dependOnClassesThat().resideInAnyPackage("com.bbcc.kidly", "com.bbcc.kidly.support..")
        .because("주문 테스트는 주문 · shared · common만으로 돈다(#616)");

    @Test
    @DisplayName("검사 대상에 주문 테스트 코드(통합 · 기능 · Kafka 테스트, 헬퍼, 테스트 기반, testFixtures)가 들어 있다")
    void importsTestCode() {
        // 테스트 클래스는 패키지 전용이라 이름으로 본다
        assertThat(ORDER_CODE.contain(ORDER + ".OrderFlowApiTest")).isTrue();
        assertThat(ORDER_CODE.contain(ORDER + ".in.event.OrderPaymentEventKafkaIntegrationTest")).isTrue();
        assertThat(ORDER_CODE.contain(OrderKafkaListenerTestGuard.class)).isTrue();
        assertThat(ORDER_CODE.contain(IntegrationTest.class)).isTrue();
        assertThat(ORDER_CODE.contain(OrderTestApplication.class)).isTrue();
        assertThat(ORDER_CODE.contain(ReplicaFixture.class)).isTrue();
    }

    @Test
    @DisplayName("주문 모듈 코드는 다른 도메인 · 앱 모듈을 참조하지 않는다")
    void noOtherDomainOrApp() {
        NO_OTHER_DOMAIN_OR_APP.check(ORDER_CODE);
    }
}
