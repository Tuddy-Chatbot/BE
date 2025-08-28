package io.github.tuddychatbot.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;


// LLM 호출용 RestClient, 그 하부전송기 ClientHttpRequestFactory 분리 빈으로 등록
@Configuration
public class RagClientConfig {

    @Bean(name = "ragRestClient")
    RestClient ragRestClient(@Value("${rag.api.base-url}") String baseUrl,
                             @Qualifier("ragClientFactory") ClientHttpRequestFactory factory) {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @Bean(name = "ragClientFactory")
    ClientHttpRequestFactory ragClientFactory(
            @Value("${rag.api.connect-timeout:PT5S}") Duration connect,
            @Value("${rag.api.read-timeout:PT30S}") Duration read) {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout((int) connect.toMillis());
        f.setReadTimeout((int) read.toMillis());
        return f;
    }
}
