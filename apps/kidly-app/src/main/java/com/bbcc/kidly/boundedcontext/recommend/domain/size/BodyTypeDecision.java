package com.bbcc.kidly.boundedcontext.recommend.domain.size;

import com.bbcc.kidly.shared.childprofile.domain.BodyType;

record BodyTypeDecision(BodyType bodyType, Basis basis) {

    enum Basis {
        UNCHANGED, ADJUSTED, HEIGHT_ONLY
    }

    static BodyTypeDecision unchanged(BodyType bodyType) {
        return new BodyTypeDecision(bodyType, Basis.UNCHANGED);
    }

    static BodyTypeDecision adjusted(BodyType judged) {
        return new BodyTypeDecision(judged, Basis.ADJUSTED);
    }

    static BodyTypeDecision heightOnly() {
        return new BodyTypeDecision(null, Basis.HEIGHT_ONLY);
    }
}
