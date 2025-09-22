package io.github.tuddy.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.tuddy.dto.UploadedFileResponse;
import io.github.tuddy.security.SecurityUtils;
import io.github.tuddy.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "파일 관리 API", description = "사용자가 업로드한 파일의 목록을 조회하고 삭제하는 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    // 현재 로그인한 사용자가 업로드한 모든 파일 목록을 조회
    @Operation(summary = "내 파일 목록 조회", description = "현재 로그인한 사용자가 업로드한 모든 파일의 목록을 최신순으로 조회")
    @GetMapping
    public ResponseEntity<List<UploadedFileResponse>> getMyFiles() {
        Long userId = SecurityUtils.requireUserId();
        List<UploadedFileResponse> files = fileService.getMyFiles(userId);
        return ResponseEntity.ok(files);
    }

    // 특정 파일을 삭제
    @Operation(summary = "파일 삭제", description = "특정 파일을 DB와 S3에서 영구적으로 삭제")
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long fileId) {
        Long userId = SecurityUtils.requireUserId();
        fileService.deleteFile(userId, fileId);
        return ResponseEntity.noContent().build(); // 성공적으로 삭제 시 204 No Content 응답
    }
}