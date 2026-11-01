package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 모듈의 패키지 배치(#651). 계층 규칙(LayerRules) · 컨텍스트 사이 규칙과 따로, 역할별 자리를 고정한다.
 * <ul>
 *   <li>컨텍스트 바로 아래에는 app · domain · in · out만 둔다(새 폴더를 만들지 않는다)</li>
 *   <li>설정은 계층마다 config 한 곳: 스프링 설정(@Configuration)은 in/config · out/config, 설정 값(@ConfigurationProperties)은
 *       app · in/config · out/config. config에는 설정만 둔다</li>
 *   <li>out/persistence에는 Spring Data JPA 저장소(인터페이스)만 둔다. 아웃박스 · DLT · 잠금 · Kafka는 각자 패키지에 둔다</li>
 *   <li>관리자 API(/api/v1/admin/**) 컨트롤러는 in/web/admin에만 두고, in/web/admin의 컨트롤러는 관리자 경로만 쓴다</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.bbcc.kidly.boundedcontext",
    importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class OrderPackageLayoutArchitectureTest {

    private static final String BASE = "com.bbcc.kidly.boundedcontext.";
    private static final String ALL = "com.bbcc.kidly.boundedcontext..";
    private static final Set<String> LAYERS = Set.of("app", "domain", "in", "out");
    private static final String ADMIN_PATH = "/api/v1/admin";
    private static final String[] CONFIG = {BASE + "*.in.config..", BASE + "*.out.config.."};

    @ArchTest
    static final ArchRule CONTEXTS_HAVE_ONLY_LAYERS = classes()
        .that().resideInAPackage(ALL)
        .and().doNotHaveSimpleName("package-info")
        .should(resideInLayerOfContext())
        .as("컨텍스트 바로 아래에는 app · domain · in · out만 둔다");

    @ArchTest
    static final ArchRule CONFIGURATIONS_LIVE_IN_CONFIG = classes()
        .that().resideInAPackage(ALL)
        .and().areMetaAnnotatedWith(Configuration.class)
        .should().resideInAnyPackage(CONFIG)
        .as("스프링 설정(@Configuration)은 in/config · out/config에 둔다");

    // app 설정 값은 app/config로 모으기 전까지(#651-1) app 기능 패키지에 둔다
    @ArchTest
    static final ArchRule PROPERTIES_LIVE_IN_APP_OR_CONFIG = classes()
        .that().resideInAPackage(ALL)
        .and().areAnnotatedWith(ConfigurationProperties.class)
        .should().resideInAnyPackage(BASE + "*.app..", CONFIG[0], CONFIG[1])
        .as("설정 값(@ConfigurationProperties)은 app · in/config · out/config에 둔다");

    @ArchTest
    static final ArchRule CONFIG_HOLDS_ONLY_SETTINGS = classes()
        .that().resideInAnyPackage(CONFIG)
        .and().areTopLevelClasses()
        .should().beMetaAnnotatedWith(Configuration.class)
        .orShould().beAnnotatedWith(ConfigurationProperties.class)
        .allowEmptyShould(true)
        .as("in/config · out/config에는 설정(@Configuration · @ConfigurationProperties)만 둔다");

    @ArchTest
    static final ArchRule PERSISTENCE_HAS_ONLY_JPA_REPOSITORIES = classes()
        .that().resideInAPackage(BASE + "*.out.persistence..")
        .should().beInterfaces()
        .andShould().beAssignableTo(Repository.class)
        .as("out/persistence에는 Spring Data JPA 저장소(인터페이스)만 둔다");

    @ArchTest
    static final ArchRule NO_MESSAGING_PACKAGE = noClasses()
        .should().resideInAPackage(BASE + "*.out.messaging..")
        .as("out/messaging은 outbox · kafka · deadletter로 나눴다(#651)");

    @ArchTest
    static final ArchRule ADMIN_CONTROLLERS_LIVE_IN_WEB_ADMIN = classes()
        .that().resideInAPackage(ALL)
        .and().areAnnotatedWith(RestController.class)
        .should(beInWebAdminIffMappedToAdminPath())
        .as("관리자 API 컨트롤러는 in/web/admin에만, in/web/admin 컨트롤러는 관리자 경로만");

    private static ArchCondition<JavaClass> resideInLayerOfContext() {
        return new ArchCondition<>("컨텍스트의 app · domain · in · out 아래에 있다") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String[] parts = javaClass.getPackageName().substring(BASE.length()).split("\\.");
                if (parts.length < 2 || !LAYERS.contains(parts[1])) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                        javaClass.getName() + "는 컨텍스트의 app · domain · in · out 밖에 있다"));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> beInWebAdminIffMappedToAdminPath() {
        return new ArchCondition<>("관리자 경로면 in/web/admin에 있다") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                boolean adminPath = javaClass.tryGetAnnotationOfType(RequestMapping.class)
                    .map(mapping -> Arrays.stream(mapping.value().length > 0 ? mapping.value() : mapping.path())
                        .anyMatch(path -> path.startsWith(ADMIN_PATH)))
                    .orElse(false);
                boolean inWebAdmin = javaClass.getPackageName().contains(".in.web.admin");
                if (adminPath != inWebAdmin) {
                    events.add(SimpleConditionEvent.violated(javaClass, javaClass.getName()
                        + (adminPath ? "는 관리자 경로인데 in/web/admin 밖에 있다" : "는 in/web/admin에 있는데 관리자 경로가 아니다")));
                }
            }
        };
    }
}
