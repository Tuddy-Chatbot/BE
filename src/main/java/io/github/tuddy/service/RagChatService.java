package io.github.tuddy.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import io.github.tuddy.dto.FastApiChatRequest;

@Service
public class RagChatService {
  private final RestClient client;
  private final String chatPath;
  private final String normalPath;

  public RagChatService(@Qualifier("ragRestClient") RestClient client,
                        @Value("${rag.api.chat-path:/rag/chat}") String chatPath,
                        @Value("${rag.api.normal-path:/normal/chat}") String normalPath) {
    this.client = client;
    this.chatPath = chatPath;
    this.normalPath = normalPath;
  }

  public String relayRag(FastApiChatRequest request) {
    try {
      return client.post().uri(chatPath)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request) // 수정된 요청 DTO 사용
        .retrieve()
        .body(String.class);
    } catch (RestClientResponseException e) {
      return e.getResponseBodyAsString();
    }
  }

  public String relayNormal(FastApiChatRequest request) {
	     try {
	      return client.post().uri(normalPath)
	        .contentType(MediaType.APPLICATION_JSON)
	        .body(request) // 수정된 요청 DTO 사용
	        .retrieve()
	        .body(String.class);
	    } catch (RestClientResponseException e) {
	      return e.getResponseBodyAsString();
	  }
	}

  // 텍스트 데이터와 이미지 파일 목록을 함께 FastAPI로 전송 (multipart/form-data)
  public String relayChatWithImages(String path, FastApiChatRequest request, List<MultipartFile> files) {
      try {
          // 1. multipart 요청 본문을 만들어주는 빌더를 생성
          MultipartBodyBuilder builder = new MultipartBodyBuilder();

          String sessionId = request.sessionId() != null ? request.sessionId().toString() : "";

          // 2. 텍스트 데이터를 파트(part)로 추가 : FastAPI의 Form(...) 파라미터 이름과 일치해야 함
          builder.part("user_id", request.userId());
          builder.part("session_id", request.sessionId());
          builder.part("query", request.query());
          builder.part("n_turns", String.valueOf(request.nTurns())); // 숫자를 문자열로 변환하여 추가

          // 3. 이미지 파일들을 파트(part)로 추가
          if (files != null && !files.isEmpty()) {
              for (MultipartFile file : files) {
                  // FastAPI의 files: List[UploadFile] = File(...) 파라미터 이름("files")과 일치해야 함
                  builder.part("files", file.getResource());
              }
          }

          // 4. RestClient를 사용하여 multipart/form-data 형식으로 POST 요청
          return client.post()
                  .uri(path) // 동적으로 RAG 경로 또는 일반 경로를 사용
                  .contentType(MediaType.MULTIPART_FORM_DATA)
                  .body(builder.build()) // 완성된 요청 본문을 설정
                  .retrieve()
                  .body(String.class);

      } catch (RestClientResponseException e) {
          return e.getResponseBodyAsString();
      }
  }

  public String getChatPath() {
      return this.chatPath;
  }

  public String getNormalPath() {
      return this.normalPath;
  }
}
