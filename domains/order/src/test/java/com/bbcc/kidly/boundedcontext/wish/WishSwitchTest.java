package com.bbcc.kidly.boundedcontext.wish;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.cart.app.port.in.CartReservations;
import com.bbcc.kidly.boundedcontext.order.app.port.in.OrderEventIssuer;
import com.bbcc.kidly.boundedcontext.order.app.port.in.ReplicaLookup;
import com.bbcc.kidly.boundedcontext.wish.app.WishExpiryService;
import com.bbcc.kidly.boundedcontext.wish.app.WishPolicy;
import com.bbcc.kidly.boundedcontext.wish.app.WishPurchaseService;
import com.bbcc.kidly.boundedcontext.wish.app.WishRequestService;
import com.bbcc.kidly.boundedcontext.wish.app.config.WishExpiryProperties;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishPurchaseRepository;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishRequestRepository;
import com.bbcc.kidly.boundedcontext.wish.in.event.WishCartEventKafkaListener;
import com.bbcc.kidly.boundedcontext.wish.in.event.WishCartSpringEventReceiver;
import com.bbcc.kidly.boundedcontext.wish.in.scheduler.WishExpiryScheduler;
import com.bbcc.kidly.boundedcontext.wish.in.scheduler.WishReservationValidUntilSync;
import com.bbcc.kidly.boundedcontext.wish.in.web.WishRequestController;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * 조르기 스위치 kidly.order.wish.enabled(#640 · #999). 공용 테스트 컨텍스트에 설정을 붙이지 않도록, 조르기 패키지만 스캔하는
 * 작은 컨텍스트(ApplicationContextRunner)로 켜짐 · 꺼짐을 본다. JPA 저장소는 이 스캔에 잡히지 않으므로 같은 조건이 붙었는지를
 * 클래스로 확인한다(Spring Data도 저장소 인터페이스의 조건을 따른다).
 */
class WishSwitchTest {

    private static final String WISH = "com.bbcc.kidly.boundedcontext.wish";
    private static final String SWITCH = "kidly.order.wish.enabled";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(WishScan.class)
        .withBean(ReplicaLookup.class, () -> mock(ReplicaLookup.class))
        .withBean(WishRequestRepository.class, () -> mock(WishRequestRepository.class))
        .withBean(WishPurchaseRepository.class, () -> mock(WishPurchaseRepository.class))
        .withBean(OrderEventIssuer.class, () -> mock(OrderEventIssuer.class))
        .withBean(CartReservations.class, () -> mock(CartReservations.class))
        .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
        .withBean(WishExpiryProperties.class, () -> new WishExpiryProperties(true, Duration.ofDays(7),
            Duration.ofHours(1), Duration.ofMinutes(15)))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .withBean(EntityManagerFactory.class, () -> mock(EntityManagerFactory.class));

    @Test
    @DisplayName("스위치가 없으면(기본) 조르기 API · 서비스 · 정책 · 결제 완료 수신(#999)이 뜬다")
    void onByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(WishPolicy.class);
            assertThat(context).hasSingleBean(WishRequestController.class);
            assertThat(context).hasSingleBean(WishRequestService.class);
            assertThat(context).hasSingleBean(WishPurchaseService.class);
            assertThat(context).hasSingleBean(WishCartEventKafkaListener.class);
            assertThat(context).hasSingleBean(WishCartSpringEventReceiver.class);
            assertThat(context).hasSingleBean(WishExpiryService.class);
            assertThat(context).hasSingleBean(WishExpiryScheduler.class);
            assertThat(context).hasSingleBean(WishReservationValidUntilSync.class);
        });
    }

    @Test
    @DisplayName("만료 스위치(kidly.order.wish.expiry.enabled)가 false면 만료 주기 작업이 뜨지 않는다(#1008). 조르기 API와 "
        + "기동 때 예약 기한 맞추기(꺼지면 기한을 없앤다)는 그대로")
    void expiryOffWhenDisabled() {
        runner.withPropertyValues("kidly.order.wish.expiry.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(WishExpiryScheduler.class);
            assertThat(context).hasSingleBean(WishRequestController.class);
            assertThat(context).hasSingleBean(WishReservationValidUntilSync.class);
        });
    }

    @Test
    @DisplayName("스위치가 false면 조르기 API · 서비스 · 정책 · 결제 완료 수신(#999)이 뜨지 않는다")
    void offWhenDisabled() {
        runner.withPropertyValues(SWITCH + "=false").run(context -> {
            assertThat(context).doesNotHaveBean(WishPolicy.class);
            assertThat(context).doesNotHaveBean(WishRequestController.class);
            assertThat(context).doesNotHaveBean(WishRequestService.class);
            assertThat(context).doesNotHaveBean(WishPurchaseService.class);
            assertThat(context).doesNotHaveBean(WishCartEventKafkaListener.class);
            assertThat(context).doesNotHaveBean(WishCartSpringEventReceiver.class);
            assertThat(context).doesNotHaveBean(WishExpiryService.class);
            assertThat(context).doesNotHaveBean(WishExpiryScheduler.class);
            assertThat(context).doesNotHaveBean(WishReservationValidUntilSync.class);
        });
    }

    @Test
    @DisplayName("조르기의 모든 빈(컴포넌트 · JPA 저장소)에 같은 스위치 조건이 붙어 있다")
    void everyBeanHasSwitch() {
        classes()
            .that(new DescribedPredicate<JavaClass>("스프링 빈(컴포넌트 · Spring Data 저장소)") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.isMetaAnnotatedWith(Component.class) || javaClass.isAssignableTo(Repository.class);
                }
            })
            .should(new ArchCondition<JavaClass>("조르기 스위치 조건이 붙어 있다") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    boolean switched = javaClass.tryGetAnnotationOfType(ConditionalOnProperty.class)
                        .filter(condition -> Arrays.asList(condition.name()).equals(List.of(SWITCH))
                            && condition.havingValue().equals("true") && condition.matchIfMissing())
                        .isPresent();
                    if (!switched) {
                        events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName() + "에 스위치 조건이 없다"));
                    }
                }
            })
            .check(new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(WISH));
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(WISH)
    static class WishScan {
    }
}
