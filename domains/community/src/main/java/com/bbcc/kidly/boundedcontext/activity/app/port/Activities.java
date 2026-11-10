package com.bbcc.kidly.boundedcontext.activity.app.port;

import com.bbcc.kidly.boundedcontext.activity.domain.Activity;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 활동 행 저장(넣기만, 고치거나 지우지 않는다). 부르는 쪽 트랜잭션에서 쓴다. 구현은 out/store.
 */
public interface Activities {

    /**
     * @param now 사실이 생긴 시각이자 넣은 시각
     */
    void insert(Activity activity, LocalDateTime now);

    /**
     * 여러 건을 한 문장으로 넣는다(건수와 상관없이 쿼리 1번).
     *
     * @return 넣은 행 수
     */
    int insertAll(List<Activity> activities, LocalDateTime now);
}
