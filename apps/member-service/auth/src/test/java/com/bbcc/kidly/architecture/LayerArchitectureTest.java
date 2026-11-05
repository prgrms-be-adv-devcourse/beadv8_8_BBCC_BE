package com.bbcc.kidly.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

/**
 * 이 도메인 모듈 안의 계층 규칙({@link LayerRules})을 검사한다.
 * 클래스패스에는 이 도메인만 있어 boundedcontext 전체를 읽어도 자기 코드만 검사된다.
 */
@AnalyzeClasses(packages = "com.bbcc.kidly.boundedcontext",
    importOptions = {ImportOption.DoNotIncludeTests.class, DoNotIncludeTestFixtures.class})
class LayerArchitectureTest {

    @ArchTest
    static final ArchTests LAYER_RULES = ArchTests.in(LayerRules.class);
}
