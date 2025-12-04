package OrangeCloud.UserRepo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "workspaceId")
public class Workspace {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "workspaceId", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID workspaceId;

    @Column(name = "ownerId", nullable = false, columnDefinition = "UUID")
    private UUID ownerId;

    @Column(name = "workspaceName", nullable = false)
    private String workspaceName;

    @Column(name = "workspaceDescription", nullable = false)
    private String workspaceDescription;

    @Column(name = "isPublic", nullable = false)
    @Builder.Default
    private Boolean isPublic = true; // 검색 > 초대 가능성

    @Column(name = "needApproved", nullable = false)
    @Builder.Default
    private Boolean needApproved = true; // workspace에 들어오는 기본 승인 필요

    @CreationTimestamp
    @Column(name = "createdAt", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deletedAt")
    private LocalDateTime deletedAt;

    // 소프트 삭제를 위한 필드
    @Column(name = "isActive", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    public void softDelete() {
        this.isActive = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void reactivate() {
        this.isActive = true;
        this.deletedAt = null;
    }
}