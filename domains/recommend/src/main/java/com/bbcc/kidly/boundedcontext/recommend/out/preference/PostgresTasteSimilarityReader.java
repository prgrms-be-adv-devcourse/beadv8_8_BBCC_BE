package com.bbcc.kidly.boundedcontext.recommend.out.preference;

import com.bbcc.kidly.boundedcontext.recommend.app.port.preference.TasteSimilarityReader;
import com.bbcc.kidly.boundedcontext.recommend.domain.embedding.EmbeddingVector;
import java.util.Collection;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class PostgresTasteSimilarityReader implements TasteSimilarityReader {

    private final JdbcClient jdbcClient;

    public PostgresTasteSimilarityReader(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Map<Long, Double> similarities(EmbeddingVector taste, Collection<Long> productIds) {
        throw new UnsupportedOperationException();
    }
}
