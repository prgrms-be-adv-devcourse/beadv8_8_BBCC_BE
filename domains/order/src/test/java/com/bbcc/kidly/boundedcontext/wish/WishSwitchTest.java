package com.bbcc.kidly.boundedcontext.wish;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.bbcc.kidly.boundedcontext.order.app.port.in.ReplicaLookup;
import com.bbcc.kidly.boundedcontext.wish.app.WishPolicy;
import com.bbcc.kidly.boundedcontext.wish.app.WishService;
import com.bbcc.kidly.boundedcontext.wish.app.port.WishItemRepository;
import com.bbcc.kidly.boundedcontext.wish.in.web.WishController;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Component;

/**
 * 조르기 스위치 kidly.order.wish.enabled(#640). 공용 테스트 컨텍스트에 설정을 붙이지 않도록, 조르기 패키지만 스캔하는
 * 작은 컨텍스트(ApplicationContextRunner)로 켜짐 · 꺼짐을 본다. JPA 저장소는 이 스캔에 잡히지 않으므로 같은 조건이 붙었는지를
 * 클래스로 확인한다(Spring Data도 저장소 인터페이스의 조건을 따른다).
 */
class WishSwitchTest {

    private static final String WISH = "com.bbcc.kidly.boundedcontext.wish";
    private static final String SWITCH = "kidly.order.wish.enabled";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(WishScan.class)
        .withBean(ReplicaLookup.class, () -> mock(ReplicaLookup.class))
        .withBean(WishItemRepository.class, () -> mock(WishItemRepository.class));

    @Test
    @DisplayName("스위치가 없으면(기본) 조르기 API · 서비스 · 정책이 뜬다")
    void onByDefault() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(WishController.class);
            assertThat(context).hasSingleBean(WishService.class);
            assertThat(context).hasSingleBean(WishPolicy.class);
        });
    }

    @Test
    @DisplayName("스위치가 false면 조르기 API · 서비스 · 정책이 뜨지 않는다")
    void offWhenDisabled() {
        runner.withPropertyValues(SWITCH + "=false").run(context -> {
            assertThat(context).doesNotHaveBean(WishController.class);
            assertThat(context).doesNotHaveBean(WishService.class);
            assertThat(context).doesNotHaveBean(WishPolicy.class);
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
                        .filter(condition -> Arrays.asList(condition.name()).equals(java.util.List.of(SWITCH))
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
