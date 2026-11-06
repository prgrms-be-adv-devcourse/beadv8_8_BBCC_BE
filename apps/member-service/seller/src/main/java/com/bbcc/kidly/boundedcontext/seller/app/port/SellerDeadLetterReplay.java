package com.bbcc.kidly.boundedcontext.seller.app.port;

import com.bbcc.kidly.boundedcontext.seller.app.dto.SellerDeadLetterMessage;

public interface SellerDeadLetterReplay {

    void replay(SellerDeadLetterMessage message);
}
