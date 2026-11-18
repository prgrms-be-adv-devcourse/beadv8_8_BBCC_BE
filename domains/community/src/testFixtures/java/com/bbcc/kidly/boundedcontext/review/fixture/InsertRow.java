package com.bbcc.kidly.boundedcontext.review.fixture;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 커뮤니티 테스트가 표에 행을 직접 넣을 때 쓰는 INSERT 만들기. 기본값을 둔 칸만 넣고, 바꾼 칸은 그 값으로 넣는다.
 * 넣지 않은 칸은 DB 기본값을 따른다. 시각 칸의 기본은 DB의 now()다.
 *
 * @param <T> 이어 부르기용 자기 타입
 */
public abstract class InsertRow<T extends InsertRow<T>> {

    private static final Object NOW = new Object();

    private final JdbcTemplate jdbc;
    private final String table;
    private final Map<String, Object> values = new LinkedHashMap<>();

    protected InsertRow(JdbcTemplate jdbc, String table) {
        this.jdbc = jdbc;
        this.table = table;
    }

    protected abstract T self();

    protected final T set(String column, Object value) {
        values.put(column, value);
        return self();
    }

    protected final T now(String column) {
        values.put(column, NOW);
        return self();
    }

    /**
     * 행을 넣는다.
     */
    public void insert() {
        Sql sql = sql();
        jdbc.update(sql.text(), sql.args().toArray());
    }

    /**
     * 행을 넣고 만들어진 id를 돌려준다(id 칸이 있는 표만).
     */
    protected final long insertReturningId() {
        Sql sql = sql();
        Long id = jdbc.queryForObject(sql.text() + " returning id", Long.class, sql.args().toArray());
        if (id == null) {
            throw new IllegalStateException("id를 받지 못했습니다: " + table);
        }
        return id;
    }

    private Sql sql() {
        List<String> placeholders = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        values.forEach((column, value) -> {
            if (value == NOW) {
                placeholders.add("now()");
            } else {
                placeholders.add("?");
                args.add(value);
            }
        });
        return new Sql("insert into community." + table + " (" + String.join(", ", values.keySet()) + ") values ("
            + String.join(", ", placeholders) + ")", args);
    }

    private record Sql(String text, List<Object> args) {
    }
}
