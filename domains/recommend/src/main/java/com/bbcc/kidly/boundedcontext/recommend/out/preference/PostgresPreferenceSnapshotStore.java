package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.PreferenceSnapshotStore;
import com.bbcc.kidly.boundedcontext.recommend.domain.preference.PreferenceSnapshot;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PostgresPreferenceSnapshotStore implements PreferenceSnapshotStore {

    private final JdbcClient jdbcClient;

    public PostgresPreferenceSnapshotStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public void replaceChild(Long childProfileId, PreferenceSnapshot snapshot) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceMember(Long memberId, PreferenceSnapshot snapshot) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreferenceSnapshot findChild(Long childProfileId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PreferenceSnapshot findMember(Long memberId) {
        throw new UnsupportedOperationException();
    }
}
