package OrangeCloud.UserRepo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspaceMembers")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@ToString(exclude = {"user"})
@EqualsAndHashCode(of = "id") // 필드명 id로 변경
public class WorkspaceMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "workspaceMemberId", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID id; // DTO의 member.getId()에 맞춤

    @Column(name = "workspaceId", nullable = false, columnDefinition = "UUID")
    private UUID workspaceId;

    // =========================================================================
    // 💡 [수정] User 엔티티와의 관계 매핑 (DTO의 getUser() 호출 지원)
    // =========================================================================
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", insertable = false, updatable = false, nullable = false)
    private User user;

    @Column(name = "userId", nullable = false, columnDefinition = "UUID")
    private UUID userId;

    // =========================================================================

    @Column(name = "roleName", nullable = false)
    @Enumerated(EnumType.STRING)
    private WorkspaceRole role;

    // 💡 [핵심 수정] DTO에서 isDefault()를 호출하기 위해 boolean(원시 타입)으로 변경합니다.
    //    Lombok은 boolean 타입 필드에 대해 isFieldName() 형태의 Getter를 생성합니다.
    @Column(name = "isDefault", nullable = false)
    @Builder.Default
    private boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "joinedAt", updatable = false)
    private LocalDateTime joinedAt;

    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "isActive", nullable = false)
    @Builder.Default
    private Boolean isActive = true; // Boolean 객체 타입 유지

    public enum WorkspaceRole {
        OWNER,
        ADMIN,
        MEMBER
    }
}