package com.bbcc.kidly.boundedcontext.favorite.out.activity;

import com.bbcc.kidly.boundedcontext.activity.app.port.in.ActivityRecorder;
import com.bbcc.kidly.boundedcontext.favorite.app.port.FavoriteActivityLog;
import com.bbcc.kidly.boundedcontext.favorite.domain.FavoriteOwner;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 찜 쪽 활동을 활동 컨텍스트에 남긴다(값만 옮겨 담는다). 이벤트의 memberId는 보호자 회원 ID(회원 본인 찜이면 본인)다.
 */
@Component
@RequiredArgsConstructor
public class FavoriteActivityAdapter implements FavoriteActivityLog {

    private final ActivityRecorder recorder;

    @Override
    public void favoriteAdded(Long favoriteId, FavoriteOwner owner, Long productId) {
        recorder.record(new ActivityRecorder.FavoriteAdded(favoriteId, owner.guardianId(), productId,
            owner.type().name(), owner.id()));
    }

    @Override
    public void favoriteRemoved(Long favoriteId, FavoriteOwner owner, Long productId) {
        recorder.record(removed(favoriteId, owner, productId));
    }

    @Override
    public void favoritesRemovedByWithdrawal(List<RemovedFavorite> removed) {
        recorder.recordWithdrawn(removed.stream()
            .map(favorite -> removed(favorite.favoriteId(), favorite.owner(), favorite.productId()))
            .toList());
    }

    private static ActivityRecorder.FavoriteRemoved removed(Long favoriteId, FavoriteOwner owner, Long productId) {
        return new ActivityRecorder.FavoriteRemoved(favoriteId, owner.guardianId(), productId, owner.type().name(),
            owner.id());
    }
}
