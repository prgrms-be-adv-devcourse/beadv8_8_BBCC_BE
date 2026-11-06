package com.bbcc.kidly.boundedcontext.childprofile.app.port;

import com.bbcc.kidly.boundedcontext.childprofile.app.dto.ChildProfileDeadLetterMessage;

public interface ChildProfileDeadLetterReplay {

    void replay(ChildProfileDeadLetterMessage message);
}
