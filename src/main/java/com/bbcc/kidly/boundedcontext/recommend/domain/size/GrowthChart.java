package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.shared.childprofile.domain.Gender;
import java.util.Optional;

public interface GrowthChart {

    Optional<WeightPercentiles> find(AgeGroup ageGroup, Gender gender, double heightCm);
}
