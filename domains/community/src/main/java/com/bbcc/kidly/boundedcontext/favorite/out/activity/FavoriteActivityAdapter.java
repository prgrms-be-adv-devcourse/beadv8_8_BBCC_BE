package com.bbcc.kidly.boundedcontext.favorite.out.activity;

import com.bbcc.kidly.boundedcontext.activity.app.port.in.ActivityRecorder;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteActivityLog;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 찜 쪽 활동을 활동 컨텍스트에 남긴다(값만 옮겨 담는다).
 */
@Component
@RequiredArgsConstructor
public class FavoriteActivityAdapter implements FavoriteActivityLog {

    private final ActivityRecorder recorder;

    @Override
    public void favoriteAdded(Long favoriteId, Long productId) {
        recorder.record(new ActivityRecorder.FavoriteAdded(favoriteId, productId));
    }

    @Override
    public void favoriteRemoved(Long favoriteId, Long productId) {
        recorder.record(new ActivityRecorder.FavoriteRemoved(favoriteId, productId));
    }

    @Override
    public void favoritesRemovedByWithdrawal(List<RemovedFavorite> removed) {
        recorder.recordWithdrawn(removed.stream()
            .map(favorite -> new ActivityRecorder.FavoriteRemoved(favorite.favoriteId(), favorite.productId()))
            .toList());
    }
}
