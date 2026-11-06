package com.bbcc.kidly.boundedcontext.auth.app.port;

import com.bbcc.kidly.boundedcontext.auth.app.dto.AuthDeadLetterMessage;

public interface AuthDeadLetterReplay {

    void replay(AuthDeadLetterMessage message);
}
