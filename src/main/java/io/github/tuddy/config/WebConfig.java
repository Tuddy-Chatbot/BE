package io.github.tuddy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import io.github.tuddy.converter.StringToChatProxyRequestConverter;
import lombok.RequiredArgsConstructor;

//Content-Type을 보내주지 않는 경우 :
//Spring이 application/octet-stream으로 들어온 데이터를 ChatProxyRequest 객체로 변환하는 방법을 직접 알려주는 역할
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final StringToChatProxyRequestConverter stringToChatProxyRequestConverter;

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(stringToChatProxyRequestConverter);
    }
}