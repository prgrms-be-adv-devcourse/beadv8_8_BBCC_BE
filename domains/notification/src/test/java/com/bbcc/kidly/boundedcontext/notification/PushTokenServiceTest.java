package com.bbcc.kidly.boundedcontext.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;

import com.bbcc.kidly.boundedcontext.notification.app.PushTokenService;
import com.bbcc.kidly.boundedcontext.notification.domain.PushTokenRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PushTokenServiceTest {

    @Mock
    private PushTokenRepository pushTokenRepository;

    @InjectMocks
    private PushTokenService pushTokenService;

    @Test
    @DisplayName("등록한 뒤 회원당 상한(10개)을 넘는 오래된 FID를 지운다")
    void registerThenTrim() {
        pushTokenService.register(1L, "fid-1");

        var order = inOrder(pushTokenRepository);
        order.verify(pushTokenRepository).register(eq(1L), eq("fid-1"), any());
        order.verify(pushTokenRepository).deleteAllButNewest(1L, 10);
    }
}
