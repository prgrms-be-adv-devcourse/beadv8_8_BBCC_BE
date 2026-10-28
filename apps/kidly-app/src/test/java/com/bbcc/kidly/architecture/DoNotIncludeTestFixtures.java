package com.bbcc.kidly.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * 도메인 모듈의 test fixtures(Fixture · 테스트 헬퍼)를 검사 대상에서 뺀다.
 * {@link ImportOption.DoNotIncludeTests}는 test 출력 폴더만 알아서 *-test-fixtures.jar는 운영 코드로 들어온다.
 * 규칙 예외가 아니라 원래 범위(테스트 코드 제외)를 되돌리는 것이다.
 */
class DoNotIncludeTestFixtures implements ImportOption {

    @Override
    public boolean includes(Location location) {
        // Gradle 실행은 *-test-fixtures.jar, IDE 빌드는 build/classes/java/testFixtures 폴더에서 읽는다
        return !location.contains("-test-fixtures.jar") && !location.contains("/testFixtures/");
    }
}
