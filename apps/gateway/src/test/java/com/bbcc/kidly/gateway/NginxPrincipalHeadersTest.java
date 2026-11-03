package com.bbcc.kidly.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.bbcc.kidly.shared.auth.PrincipalHeaders;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class NginxPrincipalHeadersTest {
    @Test
    void nginxClearsEveryPrincipalHeader() throws IOException {
        var pattern = Pattern.compile("^\\s*proxy_set_header\\s+(X-[\\w-]+)\\s+\"\";\\s*$");
        try (var lines = Files.lines(Path.of("deploy/nginx/kidly.conf.template"))) {
            var headers = lines.map(pattern::matcher).filter(matcher -> matcher.matches())
                .map(matcher -> matcher.group(1)).toList();
            assertThat(headers).containsExactlyInAnyOrderElementsOf(PrincipalHeaders.ALL);
        }
    }
}
