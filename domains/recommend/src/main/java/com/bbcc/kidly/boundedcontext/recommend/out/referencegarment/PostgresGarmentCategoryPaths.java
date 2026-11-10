package com.bbcc.kidly.boundedcontext.recommend.out.referencegarment;

import com.bbcc.kidly.boundedcontext.recommend.app.port.referencegarment.GarmentCategoryPaths;
import com.bbcc.kidly.boundedcontext.recommend.domain.referencegarment.GarmentCategoryPathEntry;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PostgresGarmentCategoryPaths implements GarmentCategoryPaths {

    @Override
    public List<GarmentCategoryPathEntry> findPathOf(Long categoryId) {
        return List.of();
    }
}
