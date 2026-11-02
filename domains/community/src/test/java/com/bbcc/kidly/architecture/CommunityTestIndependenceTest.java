package com.bbcc.kidly.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.boundedcontext.support.CommunityTestApplication;
import com.bbcc.kidly.boundedcontext.support.IntegrationTest;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 커뮤니티 테스트는 다른 도메인에 의존하지 않는다. 커뮤니티 모듈(리뷰 · 찜 컨텍스트)의 코드 전체(main · 테스트)를 읽어
 * 다른 도메인 패키지 · 앱 모듈(루트 패키지 · 앱 공통 테스트 기반 support)을 참조하지 않는지 본다. 커뮤니티는 커뮤니티 · shared ·
 * common(global)과 라이브러리만 쓴다. 다른 도메인 모듈은 클래스패스에 없어(kidly.domain) 지금은 컴파일부터 안 되지만, 그 조건이
 * 바뀌어도 이 규칙이 남도록 고정한다.
 */
class CommunityTestIndependenceTest {

    private static final String REVIEW = "com.bbcc.kidly.boundedcontext.review";
    private static final String FAVORITE = "com.bbcc.kidly.boundedcontext.favorite";
    // 커뮤니티 모듈 테스트 지원(두 컨텍스트가 같이 씀). 앱 공통 테스트 기반(com.bbcc.kidly.support)과는 다른 패키지다
    private static final String SUPPORT = "com.bbcc.kidly.boundedcontext.support";

    private static final JavaClasses COMMUNITY_CODE =
        new ClassFileImporter().importPackages(REVIEW, FAVORITE, SUPPORT);

    private static final ArchRule NO_OTHER_DOMAIN_OR_APP = noClasses()
        .should().dependOnClassesThat(resideInAPackage("com.bbcc.kidly.boundedcontext..")
            .and(not(resideInAnyPackage(REVIEW + "..", FAVORITE + "..", SUPPORT + ".."))))
        .orShould().dependOnClassesThat().resideInAnyPackage("com.bbcc.kidly", "com.bbcc.kidly.support..")
        .because("커뮤니티 테스트는 커뮤니티 · shared · common만으로 돈다");

    @Test
    @DisplayName("검사 대상에 커뮤니티 테스트 코드(테스트 기반 · 통합 테스트)가 들어 있다")
    void importsTestCode() {
        // 테스트 클래스는 패키지 전용이라 이름으로 본다
        assertThat(COMMUNITY_CODE.contain(SUPPORT + ".CommunityTestContextIntegrationTest")).isTrue();
        assertThat(COMMUNITY_CODE.contain(IntegrationTest.class)).isTrue();
        assertThat(COMMUNITY_CODE.contain(CommunityTestApplication.class)).isTrue();
    }

    @Test
    @DisplayName("커뮤니티 모듈 코드는 다른 도메인 · 앱 모듈을 참조하지 않는다")
    void noOtherDomainOrApp() {
        NO_OTHER_DOMAIN_OR_APP.check(COMMUNITY_CODE);
    }
}
