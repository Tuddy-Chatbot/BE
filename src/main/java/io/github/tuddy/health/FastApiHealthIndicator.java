package io.github.tuddy.health;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class FastApiHealthIndicator implements HealthIndicator {

    private final RestClient ragClient;

    // RagClientConfig 에서 제공하는 RestClient 빈 이름이 "ragRestClient" 임을 가정
    public FastApiHealthIndicator(@Qualifier("ragRestClient") RestClient ragClient) {
        this.ragClient = ragClient;
    }

    @Override
    public Health health() {
        try {
            // FastAPI가 가진 가장 가벼운 엔드포인트로 핑. /health 가 있으면 그쪽으로 교체
            ragClient.get().uri("/").retrieve().toBodilessEntity();
            return Health.up().withDetail("fastapi", "reachable").build();
        } catch (Exception e) {
            return Health.down(e).withDetail("fastapi", "unreachable").build();
        }
    }
}
