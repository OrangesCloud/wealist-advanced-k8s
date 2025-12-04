package OrangeCloud.UserRepo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "userProfile",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"userId", "workspaceId"})
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "profileId", columnDefinition = "UUID")
    private UUID profileId;

    @Column(name = "workspaceId", nullable = false)
    private UUID workspaceId;

    @Column(name = "userId", columnDefinition = "UUID", nullable = false)
    private UUID userId;

    @Column(name = "nickName", length = 50)
    private String nickName;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "profileImageUrl")
    private String profileImageUrl;

    @CreationTimestamp
    @Column(name = "createdAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    // =========================================================================
    // 💡 업데이트 로직 (Service에서 호출)
    // =========================================================================

    public void updateNickName(String nickName) {
        this.nickName = nickName;
    }

    public void updateEmail(String email) {
        this.email = email;
    }

    public void updateProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public void updateWorkspaceId(UUID workspaceId) {
        this.workspaceId = workspaceId;
    }
    public static UserProfile create(UUID workspaceId,
                                     UUID userId,
                                     String nickName,
                                     String email,
                                     String profileImageUrl) {
        return UserProfile.builder()
                .workspaceId(workspaceId)
                .userId(userId)
                .nickName(nickName)
                .email(email)
                .profileImageUrl(profileImageUrl)
                .build();
    }



}