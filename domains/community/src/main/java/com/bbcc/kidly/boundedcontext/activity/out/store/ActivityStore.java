package com.bbcc.kidly.boundedcontext.activity.out.store;

import com.bbcc.kidly.boundedcontext.activity.app.port.Activities;
import com.bbcc.kidly.boundedcontext.activity.domain.Activity;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * 활동 행을 넣는다(JDBC, 부르는 쪽 트랜잭션). 속성은 키 이름 → 값 JSON(jsonb)이다. 여러 건은 배열을 펼쳐 한 문장으로 넣는다.
 */
@Component
@RequiredArgsConstructor
public class ActivityStore implements Activities {

    private static final String INSERT = """
        insert into community.activity (occurred_at, type, product_id, aggregate_id, attributes, created_at)
        values (?, ?, ?, ?, ?::jsonb, ?)
        """;
    private static final String INSERT_ALL = """
        insert into community.activity (occurred_at, type, product_id, aggregate_id, attributes, created_at)
        select ?, t.type, t.product_id, t.aggregate_id, t.attributes::jsonb, ?
        from unnest(?::varchar[], ?::bigint[], ?::bigint[], ?::text[]) as t(type, product_id, aggregate_id, attributes)
        """;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final JdbcTemplate jdbc;

    @Override
    public void insert(Activity activity, LocalDateTime now) {
        Timestamp at = Timestamp.valueOf(now);
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(INSERT);
            statement.setTimestamp(1, at);
            statement.setString(2, activity.type().name());
            if (activity.productId() == null) {
                statement.setNull(3, Types.BIGINT);
            } else {
                statement.setLong(3, activity.productId());
            }
            statement.setLong(4, activity.aggregateId());
            statement.setString(5, json(activity));
            statement.setTimestamp(6, at);
            return statement;
        });
    }

    @Override
    public int insertAll(List<Activity> activities, LocalDateTime now) {
        if (activities.isEmpty()) {
            return 0;
        }
        Timestamp at = Timestamp.valueOf(now);
        return jdbc.update(connection -> {
            Array types = connection.createArrayOf("varchar",
                activities.stream().map(activity -> activity.type().name()).toArray());
            Array productIds = connection.createArrayOf("bigint",
                activities.stream().map(Activity::productId).toArray());
            Array aggregateIds = connection.createArrayOf("bigint",
                activities.stream().map(Activity::aggregateId).toArray());
            Array attributes = connection.createArrayOf("text", activities.stream().map(this::json).toArray());
            PreparedStatement statement = connection.prepareStatement(INSERT_ALL);
            statement.setTimestamp(1, at);
            statement.setTimestamp(2, at);
            statement.setArray(3, types);
            statement.setArray(4, productIds);
            statement.setArray(5, aggregateIds);
            statement.setArray(6, attributes);
            return statement;
        });
    }

    private String json(Activity activity) {
        return jsonMapper.writeValueAsString(activity.attributesByKey());
    }
}
