package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

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
 * 커뮤니티 모듈의 패키지 배치(주문 배치의 경량판). 계층 규칙(LayerRules) · 컨텍스트 사이 규칙과 따로, 역할별 자리를 고정한다.
 * <ul>
 *   <li>컨텍스트 바로 아래에는 app · domain · in · out만 둔다(새 폴더를 만들지 않는다)</li>
 *   <li>설정은 계층마다 config 한 곳: 스프링 설정(@Configuration)은 in/config · out/config, 설정 값(@ConfigurationProperties)은
 *       app · in/config · out/config. config에는 설정만 둔다</li>
 *   <li>out 바로 아래 하위 패키지는 persistence · config · store(저장소 조각) · {상대 컨텍스트}(어댑터)만 둔다</li>
 *   <li>out/persistence에는 Spring Data JPA 저장소(인터페이스)만 둔다</li>
 *   <li>관리자 API(/api/v1/admin/**) 컨트롤러는 in/web/admin에만 두고, in/web/admin의 컨트롤러는 관리자 경로만 쓴다</li>
 * </ul>
 * 뼈대만 있는 동안은 대상이 없어도 통과한다(allowEmptyShould).
 */
@AnalyzeClasses(packages = "com.bbcc.kidly.boundedcontext",
    importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class CommunityPackageLayoutArchitectureTest {

    private static final String BASE = "com.bbcc.kidly.boundedcontext.";
    private static final String ALL = "com.bbcc.kidly.boundedcontext..";
    private static final String PACKAGE_INFO = "package-info";
    private static final Set<String> LAYERS = Set.of("app", "domain", "in", "out");
    private static final String ADMIN_PATH = "/api/v1/admin";
    private static final String[] CONFIG = {BASE + "*.in.config..", BASE + "*.out.config.."};
    private static final Set<String> OUT_PACKAGES = Set.of("persistence", "config", "store");
    private static final Set<String> CONTEXTS = Set.of("review", "favorite");

    @ArchTest
    static final ArchRule CONTEXTS_HAVE_ONLY_LAYERS = classes()
        .that().resideInAPackage(ALL)
        .and().doNotHaveSimpleName(PACKAGE_INFO)
        .should(resideInLayerOfContext())
        .allowEmptyShould(true)
        .as("컨텍스트 바로 아래에는 app · domain · in · out만 둔다");

    @ArchTest
    static final ArchRule OUT_HAS_ONLY_KNOWN_SUBPACKAGES = classes()
        .that().resideInAPackage(BASE + "*.out.*..")
        .should(resideInKnownOutSubpackage())
        .allowEmptyShould(true)
        .as("out 바로 아래 하위 패키지는 persistence · config · store · {상대 컨텍스트}만 둔다");

    @ArchTest
    static final ArchRule CONFIGURATIONS_LIVE_IN_CONFIG = classes()
        .that().resideInAPackage(ALL)
        .and().areMetaAnnotatedWith(Configuration.class)
        .should().resideInAnyPackage(CONFIG)
        .allowEmptyShould(true)
        .as("스프링 설정(@Configuration)은 in/config · out/config에 둔다");

    @ArchTest
    static final ArchRule PROPERTIES_LIVE_IN_APP_OR_CONFIG = classes()
        .that().resideInAPackage(ALL)
        .and().areAnnotatedWith(ConfigurationProperties.class)
        .should().resideInAnyPackage(BASE + "*.app..", CONFIG[0], CONFIG[1])
        .allowEmptyShould(true)
        .as("설정 값(@ConfigurationProperties)은 app · in/config · out/config에 둔다");

    @ArchTest
    static final ArchRule CONFIG_HOLDS_ONLY_SETTINGS = classes()
        .that().resideInAnyPackage(CONFIG)
        .and().areTopLevelClasses()
        .and().doNotHaveSimpleName(PACKAGE_INFO)
        .should().beMetaAnnotatedWith(Configuration.class)
        .orShould().beAnnotatedWith(ConfigurationProperties.class)
        .allowEmptyShould(true)
        .as("in/config · out/config에는 설정(@Configuration · @ConfigurationProperties)만 둔다");

    @ArchTest
    static final ArchRule PERSISTENCE_HAS_ONLY_JPA_REPOSITORIES = classes()
        .that().resideInAPackage(BASE + "*.out.persistence..")
        .and().doNotHaveSimpleName(PACKAGE_INFO)
        .should().beInterfaces()
        .andShould().beAssignableTo(Repository.class)
        .allowEmptyShould(true)
        .as("out/persistence에는 Spring Data JPA 저장소(인터페이스)만 둔다");

    @ArchTest
    static final ArchRule ADMIN_CONTROLLERS_LIVE_IN_WEB_ADMIN = classes()
        .that().resideInAPackage(ALL)
        .and().areAnnotatedWith(RestController.class)
        .should(beInWebAdminIffMappedToAdminPath())
        .allowEmptyShould(true)
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

    private static ArchCondition<JavaClass> resideInKnownOutSubpackage() {
        return new ArchCondition<>("out의 persistence · config · store · 상대 컨텍스트 아래에 있다") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String[] parts = javaClass.getPackageName().substring(BASE.length()).split("\\.");
                String context = parts[0];
                String sub = parts[2];
                boolean known = OUT_PACKAGES.contains(sub) || (CONTEXTS.contains(sub) && !sub.equals(context));
                if (!known) {
                    events.add(SimpleConditionEvent.violated(javaClass,
                        javaClass.getName() + "는 out의 정해진 하위 패키지(persistence · config · store · 상대 컨텍스트) 밖에 있다"));
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
