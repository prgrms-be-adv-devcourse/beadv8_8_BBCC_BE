package com.bbcc.kidly.boundedcontext.review.out.storage;

import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 테스트용 업로드 확인(이름 fake). 올린 것으로 등록한 주소만 있음이고, 확인 불가로 등록한 주소는 확인 불가, 그 밖은 없음이다.
 * 커뮤니티 모듈 테스트 설정(application-test.yml)의 upload-check로 고른다.
 */
@Component
public class FakeReviewImageUploadChecker implements ReviewImageUploadChecker {

    private final Set<String> uploaded = new HashSet<>();
    private final Set<String> unavailable = new HashSet<>();
    // 확인하는 동안 일어난 일(예: 다른 수정 · 삭제 · 관리자 조치)을 흉내 낸다. 한 번 부르고 지운다
    private Runnable duringCheck;

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public Presence check(String imageUrl, String key) {
        Runnable hook;
        synchronized (this) {
            hook = duringCheck;
            duringCheck = null;
        }
        if (hook != null) {
            hook.run();
        }
        return presence(imageUrl);
    }

    private synchronized Presence presence(String imageUrl) {
        if (unavailable.contains(imageUrl)) {
            return Presence.UNKNOWN;
        }
        return uploaded.contains(imageUrl) ? Presence.PRESENT : Presence.ABSENT;
    }

    public synchronized void markUploaded(String imageUrl) {
        uploaded.add(imageUrl);
    }

    public synchronized void markUnavailable(String imageUrl) {
        unavailable.add(imageUrl);
    }

    public synchronized void duringNextCheck(Runnable hook) {
        duringCheck = hook;
    }

    public synchronized void reset() {
        uploaded.clear();
        unavailable.clear();
        duringCheck = null;
    }
}
