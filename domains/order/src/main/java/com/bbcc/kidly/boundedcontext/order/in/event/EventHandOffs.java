package com.bbcc.kidly.boundedcontext.order.in.event;

/**
 * DLT 메시지 재처리(#601)가 원래 Kafka 리스너를 부르는 동안, 리스너가 처리 실패를 주문 자체 보관(결제 반영 실패 · 이벤트 처리 실패
 * 기록)에 넘기고 정상 반환했는지 알린다. 리스너는 넘긴 경우 {@link #noteHandedOff()}만 부르고, 재처리가 지켜보지 않는 Kafka 수신
 * 중에는 아무것도 하지 않는다. 같은 스레드에서 부르므로 스레드 로컬로 둔다.
 */
final class EventHandOffs {

    private static final ThreadLocal<boolean[]> WATCHING = new ThreadLocal<>();

    private EventHandOffs() {
    }

    static void noteHandedOff() {
        boolean[] handedOff = WATCHING.get();
        if (handedOff != null) {
            handedOff[0] = true;
        }
    }

    /**
     * process를 실행하는 동안 넘김이 있었는지 지켜본다.
     *
     * @return 리스너가 주문 자체 보관에 넘겼으면 true
     */
    static boolean watch(Runnable process) {
        boolean[] handedOff = {false};
        WATCHING.set(handedOff);
        try {
            process.run();
            return handedOff[0];
        } finally {
            WATCHING.remove();
        }
    }
}
