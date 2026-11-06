package com.bbcc.kidly.boundedcontext.member.app.port;

import com.bbcc.kidly.boundedcontext.member.app.dto.MemberDeadLetterMessage;

public interface MemberDeadLetterReplay {

    void replay(MemberDeadLetterMessage message);
}
