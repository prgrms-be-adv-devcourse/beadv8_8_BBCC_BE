package com.bbcc.kidly.support;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

/**
 * 테스트용 Elasticsearch. 로컬 compose와 같은 Dockerfile(공식 이미지 + nori)로 만들어 형태소 검색까지 실제와 같게 본다.
 * 이미지는 지우지 않고 남겨 두어 다음 실행에서 다시 빌드하지 않는다. 테스트는 루트에서 실행하므로 상대 경로로 찾는다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ElasticsearchTestcontainersConfiguration {

    private static final int HTTP_PORT = 9200;

    @Bean
    GenericContainer<?> elasticsearchContainer() {
        return new GenericContainer<>(new ImageFromDockerfile("kidly-elasticsearch-test", false)
            .withDockerfile(Path.of("docker/elasticsearch/Dockerfile")))
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
            .withExposedPorts(HTTP_PORT)
            .waitingFor(Wait.forHttp("/_cluster/health?wait_for_status=yellow").forPort(HTTP_PORT)
                .withStartupTimeout(Duration.ofMinutes(3)));
    }

    @Bean
    DynamicPropertyRegistrar elasticsearchProperties(GenericContainer<?> elasticsearchContainer) {
        return registry -> registry.add("spring.elasticsearch.uris",
            () -> "http://" + elasticsearchContainer.getHost() + ":" + elasticsearchContainer.getMappedPort(HTTP_PORT));
    }
}
