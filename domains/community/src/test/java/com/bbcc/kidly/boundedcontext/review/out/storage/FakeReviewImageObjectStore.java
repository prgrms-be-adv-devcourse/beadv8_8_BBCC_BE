package com.bbcc.kidly.boundedcontext.review.out.storage;

import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.Deletion;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.ListUnavailableException;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.StoredObject;
import com.bbcc.kidly.boundedcontext.review.app.port.ReviewImageCleanupStorage.StoredPage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/**
 * 테스트용 리뷰 사진 객체 저장소(이름 fake, 업로드 확인 fake와 같은 설정 이름으로 골라진다). 넣어 둔 key · 올린 시각을 key 순으로
 * 목록에 주고(이어 읽기 표지는 그 쪽 마지막 key), 지우기 요청을 기록한다. 실패로 정한 key는 지우지 않고 FAILED, 목록 실패로 정하면 예외.
 */
@Component
public class FakeReviewImageObjectStore implements ReviewImageObjectStore {

    private final TreeMap<String, Instant> objects = new TreeMap<>();
    private final Map<String, String> failures = new HashMap<>();
    private final List<String> deleteRequests = new ArrayList<>();
    private final List<String> listRequests = new ArrayList<>();
    private boolean listUnavailable;
    // 지우는 동안 일어난 일(예: 다른 서버의 정리)을 흉내 낸다. 한 번 부르고 지운다
    private Runnable duringDelete;

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public Deletion delete(String key) {
        Runnable hook;
        synchronized (this) {
            deleteRequests.add(key);
            hook = duringDelete;
            duringDelete = null;
        }
        if (hook != null) {
            hook.run();
        }
        synchronized (this) {
            String failure = failures.get(key);
            if (failure != null) {
                return Deletion.failed(failure);
            }
            objects.remove(key);
            return Deletion.deleted();
        }
    }

    @Override
    public synchronized StoredPage list(String prefix, String continuationToken, int pageSize) {
        listRequests.add(prefix + "|" + continuationToken + "|" + pageSize);
        if (listUnavailable) {
            throw new ListUnavailableException("S3Exception:403");
        }
        List<StoredObject> page = new ArrayList<>();
        Map<String, Instant> rest = continuationToken == null ? objects : objects.tailMap(continuationToken, false);
        for (Map.Entry<String, Instant> entry : rest.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            if (page.size() == pageSize) {
                return new StoredPage(page, page.getLast().key());
            }
            page.add(new StoredObject(entry.getKey(), entry.getValue()));
        }
        return new StoredPage(page, null);
    }

    public synchronized void put(String key, Instant lastModified) {
        objects.put(key, lastModified);
    }

    public synchronized boolean contains(String key) {
        return objects.containsKey(key);
    }

    public synchronized void failDelete(String key, String failureType) {
        failures.put(key, failureType);
    }

    public synchronized void clearFailure(String key) {
        failures.remove(key);
    }

    public synchronized void listUnavailable(boolean unavailable) {
        listUnavailable = unavailable;
    }

    public synchronized void duringNextDelete(Runnable hook) {
        duringDelete = hook;
    }

    public synchronized List<String> deleteRequests() {
        return List.copyOf(deleteRequests);
    }

    public synchronized List<String> listRequests() {
        return List.copyOf(listRequests);
    }

    public synchronized void reset() {
        objects.clear();
        failures.clear();
        deleteRequests.clear();
        listRequests.clear();
        listUnavailable = false;
        duringDelete = null;
    }
}
