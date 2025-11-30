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

import io.github.tuddy.entity.file.UploadedFile;
import io.github.tuddy.security.AuthUser;
import io.github.tuddy.security.SecurityUtils;
import io.github.tuddy.service.FileService;
import io.github.tuddy.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "파일 업로드 API", description = "S3 파일 업로드/다운로드를 위한 Presigned URL 발급 API")
@RestController
@RequestMapping("/s3")
@RequiredArgsConstructor
public class UploadController {

    private final S3Service s3;
    private final FileService fileService;

    @Operation(summary = "파일 업로드를 위한 Presigned URL 생성", description = "S3에 파일을 직접 업로드할 수 있는 1회용 URL과 DB에 저장될 파일 정보를 생성합니다.")
    @Parameter(name = "filename", description = "업로드할 원본 파일 이름", required = true, example = "lecture.pdf")
    @Parameter(name = "contentType", description = "파일의 MIME 타입", required = true, example = "application/pdf")
    @Parameter(name = "contentLength", description = "파일의 전체 크기 (bytes)", required = true, example = "1048576")
    @PostMapping("/put")
    public ResponseEntity<?> presignPut(@RequestParam String filename,
                                        @RequestParam String contentType,
                                        @RequestParam long contentLength,
                                        @AuthenticationPrincipal AuthUser user) {
        // [수정] 인증되지 않은 사용자 접근 차단
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Long uid = user.getId(); // 0L fallback 제거

            // 1. S3 Key 생성 및 Presigned URL 발급
            String key = s3.buildKey(uid, filename);
            Map<String, Object> res = s3.presignPut(uid, filename, contentType, contentLength, key);

            // 2. DB에 메타데이터 저장
            UploadedFile savedFile = fileService.createFileMetadata(uid, filename, key);

            // 3. 응답에 fileId 추가
            res.put("fileId", savedFile.getId());

            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.startsWith("SIZE_LIMIT")) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", msg));
            }
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(Map.of("error", msg));
        }
    }

    @Operation(summary = "파일 조회를 위한 Presigned URL 생성", description = "S3에 저장된 파일을 다운로드할 수 있는 1회용 URL을 생성")
    @Parameter(name = "key", description = "S3에 저장된 파일의 전체 경로(key)", required = true)
    @GetMapping("/get")
    public ResponseEntity<?> presignGet(@RequestParam String key) {
        try {
            Long currentUserId = SecurityUtils.requireUserId();

            String[] parts = key.split("/");
            if (parts.length < 2) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid key format."));
            }

            Long ownerId = Long.parseLong(parts[1]);

            if (!currentUserId.equals(ownerId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You do not have permission to access this file."));
            }

            String presignedUrl = s3.presignGet(key);
            return ResponseEntity.ok(Map.of("url", presignedUrl));

        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid user ID in key."));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "File not found or access denied."));
        }
    }
}