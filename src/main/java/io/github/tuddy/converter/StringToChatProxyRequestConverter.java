package io.github.tuddy.converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.tuddy.dto.ChatProxyRequest;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

// Content-Type을 보내주지 않는 경우 :
// Spring이 application/octet-stream으로 들어온 데이터를 ChatProxyRequest 객체로 변환하는 방법을 직접 알려주는 역할
@Component
@RequiredArgsConstructor
public class StringToChatProxyRequestConverter implements Converter<String, ChatProxyRequest> {

    private final ObjectMapper objectMapper;

    @Override
    @SneakyThrows
    public ChatProxyRequest convert(String source) {
        return objectMapper.readValue(source, ChatProxyRequest.class);
    }
}