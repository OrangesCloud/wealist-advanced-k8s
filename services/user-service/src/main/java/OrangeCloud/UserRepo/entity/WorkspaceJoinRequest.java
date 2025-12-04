package OrangeCloud.UserRepo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workspaceJoinRequests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "joinRequestId")
public class WorkspaceJoinRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "joinRequestId", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID joinRequestId;

    @Column(name = "workspaceId", nullable = false, columnDefinition = "UUID")
    private UUID workspaceId;

    @Column(name = "userId", nullable = false, columnDefinition = "UUID")
    private UUID userId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @CreationTimestamp
    @Column(name = "requestedAt", updatable = false)
    private LocalDateTime requestedAt;

    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    public enum JoinRequestStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}