package io.github.tuddy.entity.file;

import java.time.LocalDateTime;

import io.github.tuddy.entity.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="uploaded_files")
public class UploadedFile {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount userAccount;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "s3_key", nullable = false, unique = true)
    private String s3Key;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default // 빌더 패턴 사용 시 기본값 적용
    private FileStatus status = FileStatus.PENDING;

    @PrePersist
    void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}