package OrangeCloud.UserRepo.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@EqualsAndHashCode(of = "userId")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "userId", updatable = false, nullable = false, columnDefinition = "UUID")
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "provider")
    @Builder.Default
    private String provider = "google";

    @Column(name = "googleId", unique = true)
    private String googleId;

    @CreationTimestamp
    @Column(name = "createdAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updatedAt")
    private LocalDateTime updatedAt;

    @Column(name = "isActive", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "deletedAt")
    private LocalDateTime deletedAt;




    public void softDelete() {
        this.isActive = false;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.isActive = true;
        this.deletedAt = null;
    }
}