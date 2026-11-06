package com.bbcc.kidly.boundedcontext.review.out.moderation;

import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.FailureCause;
import com.bbcc.kidly.boundedcontext.review.app.port.ContentModerator.Result;
import com.bbcc.kidly.boundedcontext.review.domain.ModerationReason;

class FakeModerationAdapterContractTest extends ContentModeratorContractTest {

    @Override
    protected ModerationAdapter adapterFor(Scenario scenario) {
        FakeModerationAdapter fake = new FakeModerationAdapter();
        fake.willReturn(switch (scenario) {
            case CLEAN -> Result.passed();
            case VIOLATING -> Result.flagged(ModerationReason.OTHER);
            case FAILURE -> Result.undecided(FailureCause.UNAVAILABLE);
            case TIMEOUT -> Result.undecided(FailureCause.TIMEOUT);
            case RATE_LIMIT -> Result.undecided(FailureCause.RATE_LIMITED);
        });
        return fake;
    }
}
