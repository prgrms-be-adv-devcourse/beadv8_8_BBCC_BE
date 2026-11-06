package com.bbcc.kidly.boundedcontext.recommend.domain.preference;

/**
 * 자녀 한 명의 선호 집계를 저장하고 읽는다. 카테고리 줄과 브랜드 줄을 애그리거트 하나로 본다.
 * 올리고 내리는 메서드는 호출한 쪽의 트랜잭션 안에서 실행한다.
 */
public interface ChildPreferenceRepository {

    /**
     * 자녀의 집계를 읽는다. 줄이 없으면 빈 집계를 돌려준다.
     */
    ChildPreference findByChildProfileId(Long childProfileId);

    /**
     * 활동의 몫을 집계에 더한다. 활동에 카테고리나 브랜드가 없으면 그 줄은 만들지 않는다.
     */
    void add(Long childProfileId, PreferenceActivity activity);

    /**
     * 더했던 활동의 몫을 집계에서 뺀다. 줄이 없어도 빼며, 횟수는 0에서 자르지 않는다.
     */
    void subtract(Long childProfileId, PreferenceActivity activity);
}
