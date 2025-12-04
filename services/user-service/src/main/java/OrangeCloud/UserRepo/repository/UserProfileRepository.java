package OrangeCloud.UserRepo.repository;

import OrangeCloud.UserRepo.entity.User;
import OrangeCloud.UserRepo.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * UserProfile 엔티티에 대한 데이터 접근 계층 (DAO)
 */
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    /**
     * User ID를 기반으로 UserProfile을 조회합니다.
     * @param userId User 엔티티의 ID (FK)
     * @return UserProfile 객체 (Optional)
     */
    Optional<UserProfile> findByUserId(UUID userId);
    Optional<UserProfile> findByWorkspaceId(UUID workspaceId);
    // 워크스페이스별 프로필 조회

    Optional<UserProfile> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    // 사용자의 모든 워크스페이스 프로필 조회
    List<UserProfile> findAllByUserId(UUID userId);

    // 특정 워크스페이스의 모든 사용자 프로필 조회
    List<UserProfile> findAllByWorkspaceId(UUID workspaceId);

    // 워크스페이스별 프로필 존재 여부 확인
    boolean existsByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);
    int countByUserId(UUID userId);




}