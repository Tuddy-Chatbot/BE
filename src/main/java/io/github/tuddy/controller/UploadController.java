package io.github.tuddy.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.tuddy.security.AuthUser;
import io.github.tuddy.service.S3Service;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/s3")
@RequiredArgsConstructor
public class UploadController {

  private final S3Service s3;

  // 프리사인 PUT 발급
  @PostMapping("/presign-put")
  public ResponseEntity<?> presignPut(@RequestParam String filename,
                                      @RequestParam String contentType,
                                      @RequestParam long contentLength,
                                      @AuthenticationPrincipal AuthUser user) {
    try {
      Long uid = user != null ? user.getId() : 0L;
      Map<String, Object> res = s3.presignPut(uid, filename, contentType, contentLength);
      return ResponseEntity.ok(res);
    } catch (IllegalArgumentException ex) {
      String msg = ex.getMessage();
      if (msg != null && msg.startsWith("SIZE_LIMIT")) {
        return ResponseEntity.status(413).body(Map.of("error", msg));
      }
      return ResponseEntity.status(415).body(Map.of("error", msg));
    }
  }
}
