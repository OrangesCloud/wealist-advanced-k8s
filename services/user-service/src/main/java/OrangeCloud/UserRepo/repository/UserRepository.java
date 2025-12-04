package OrangeCloud.UserRepo.repository;

import OrangeCloud.UserRepo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    // ============================================================================
    // Google OAuth 관련
    // ============================================================================

    /**
     * Google ID로 사용자 조회 (매우 중요!)
     */
    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmail(String email);

    /**
     * 이메일로 활성화된 사용자 조회
     */
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.isActive = true")
    Optional<User> findActiveByEmail(@Param("email") String email);

    // ============================================================================
    // 소프트 삭제/복구
    // ============================================================================

    /**
     * 사용자 소프트 삭제
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.isActive = false, u.deletedAt = CURRENT_TIMESTAMP WHERE u.userId = :userId")
    int softDeleteById(@Param("userId") UUID userId);

    /**
     * 사용자 재활성화
     */
    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.isActive = true, u.deletedAt = null WHERE u.userId = :userId")
    int reactivateById(@Param("userId") UUID userId);

    // ============================================================================
    // 사용자 조회
    // ============================================================================

    /**
     * 모든 활성화된 사용자 조회
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true")
    List<User> findAllActiveUsers();

    /**
     * ID로 활성화된 사용자 조회
     */
    Optional<User> findByUserIdAndIsActiveTrue(UUID userId);

    /**
     * 이메일로 활성화된 사용자 조회 (중복 검사용)
     */
    Optional<User> findByEmailAndIsActiveTrue(String email);

    /**
     * 여러 사용자 ID로 조회
     */
    List<User> findAllByUserIdIn(List<UUID> ids);

    // ============================================================================
    // 사용자 검색
    // ============================================================================

    /**
     * 이메일로 검색 (활성화된 사용자만)
     */
    List<User> findAllByEmailAndIsActiveTrue(String email);

    // ============================================================================
    // 중복 체크
    // ============================================================================

    /**
     * 이메일 중복 체크 (활성화된 사용자만)
     */
    boolean existsByEmailAndIsActiveTrue(String email);

    // ============================================================================
    // 통계
    // ============================================================================

    /**
     * 활성화된 사용자 수 조회
     */
    long countByIsActiveTrue();

    /**
     * 비활성화된 사용자 조회 (관리자용)
     */
    @Query("SELECT u FROM User u WHERE u.isActive = false ORDER BY u.deletedAt DESC")
    List<User> findInactiveUsers();

    /**
     * 생성일 기준 활성화된 사용자 조회
     */
    @Query("SELECT u FROM User u WHERE u.createdAt >= :startDate AND u.isActive = true ORDER BY u.createdAt DESC")
    List<User> findActiveUsersCreatedAfter(@Param("startDate") LocalDateTime startDate);

    // ============================================================================
    // Native Query (필요시 사용)
    // ============================================================================

    /**
     * Native Query 사용 (PostgreSQL 기준)
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE users SET \"isActive\" = false, \"deletedAt\" = NOW() WHERE \"userId\" = ?1",
            nativeQuery = true)
    int softDeleteByIdNative(UUID userId);
}