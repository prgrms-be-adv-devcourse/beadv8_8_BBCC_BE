package com.bbcc.kidly.boundedcontext.notification.app.port;

public sealed interface OutgoingMessage permits Email, PushMessage {
}
