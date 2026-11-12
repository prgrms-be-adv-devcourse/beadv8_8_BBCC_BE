package com.bbcc.kidly.gateway.ratelimit;

import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import com.bbcc.kidly.shared.auth.PrincipalType;
import java.net.InetSocketAddress;
import org.springframework.http.server.reactive.ServerHttpRequest;

/** 요청 수 제한 필터가 같이 쓰는 키. 로그인했으면 주체 ID, 아니면 클라이언트 IP다. */
final class RateLimitKeys {

    static final String REAL_IP = "X-Real-IP";

    private RateLimitKeys() {
    }

    static String keyOf(ServerHttpRequest request) {
        var headers = request.getHeaders();
        for (PrincipalType type : PrincipalType.values()) {
            var id = headers.getFirst(PrincipalHeaders.idHeader(type));
            if (id != null) {
                return type.name() + ":" + id;
            }
        }
        return "IP:" + clientIp(request);
    }

    private static String clientIp(ServerHttpRequest request) {
        var realIp = request.getHeaders().getFirst(REAL_IP);
        if (realIp != null && !realIp.isBlank()) {
            return realIp.strip();
        }
        InetSocketAddress remote = request.getRemoteAddress();
        if (remote == null) {
            return "unknown";
        }
        return remote.getAddress() == null ? remote.getHostString() : remote.getAddress().getHostAddress();
    }
}
