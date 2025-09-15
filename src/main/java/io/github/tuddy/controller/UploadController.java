package io.github.tuddy.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.tuddy.security.AuthUser;
import io.github.tuddy.security.SecurityUtils;
import io.github.tuddy.service.S3Service;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/s3")
@RequiredArgsConstructor
public class UploadController {

	private final S3Service s3;

	// 프리사인 PUT 발급
	@PostMapping("/put")
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

	// 파일 조회를 위한 Presigned GET 발급
	 @GetMapping("/get")
	  public ResponseEntity<?> presignGet(@RequestParam String key) {
	    try {
	        // 현재 로그인한 사용자의 ID를 가져옴
	        Long currentUserId = SecurityUtils.requireUserId();

	        // S3 key 경로에서 파일 소유자의 ID를 추출
	        // key 형식: "incoming/사용자ID/년/월/일/UUID-파일명"
	        String[] parts = key.split("/");
	        if (parts.length < 2) {
	            // 경로 형식이 올바르지 않으면 잘못된 요청으로 처리
	            return ResponseEntity.badRequest().body(Map.of("error", "Invalid key format."));
	        }

	        Long ownerId = Long.parseLong(parts[1]);

	        // 현재 사용자와 파일 소유자가 일치하는지 확인
	        if (!currentUserId.equals(ownerId)) {
	            // 일치하지 않으면 권한 없음(403 Forbidden) 오류를 반환
	            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to access this file."));
	        }

	        // 권한이 확인되면 Presigned URL을 생성하여 반환
	        String presignedUrl = s3.presignGet(key);
	        return ResponseEntity.ok(Map.of("url", presignedUrl));

	    } catch (NumberFormatException e) {
	        return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID in key."));
	    } catch (Exception ex) {
	        // 파일이 S3에 존재하지 않거나 할 때 S3Exception이 발생 가능
	        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "File not found or access denied."));
	    }
	  }

}
