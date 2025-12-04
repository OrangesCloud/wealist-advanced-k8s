package OrangeCloud.UserRepo.repository;

import OrangeCloud.UserRepo.entity.Workspace;
import org.hibernate.jdbc.Work;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    List<Workspace> findByOwnerId(UUID ownerId);
    // ============================================================================
    // 소프트 삭제/복구
    // ============================================================================

    /**
     * Workspace 소프트 삭제
     */
    @Modifying
    @Transactional
    @Query("UPDATE Workspace w SET w.isActive = false, w.deletedAt = CURRENT_TIMESTAMP WHERE w.workspaceId = :workspaceId")
    int softDeleteById(@Param("workspaceId") UUID workspaceId);

    /**
     * Workspace 재활성화
     */
    @Modifying
    @Transactional
    @Query("UPDATE Workspace w SET w.isActive = true, w.deletedAt = null WHERE w.workspaceId = :workspaceId")
    int reactivateById(@Param("workspaceId") UUID workspaceId);

    // ============================================================================
    // Workspace 조회
    // ============================================================================

    /**
     * 모든 활성화된 Workspace 조회
     */
    @Query("SELECT w FROM Workspace w WHERE w.isActive = true ORDER BY w.createdAt DESC")
    List<Workspace> findAllActiveWorkspaces();

    /**
     * ID로 활성화된 Workspace 조회
     */
    @Query("SELECT w FROM Workspace w WHERE w.workspaceId = :workspaceId AND w.isActive = true")
    Optional<Workspace> findActiveById(@Param("workspaceId") UUID workspaceId);

    /**
     * 이름으로 활성화된 Workspace 검색
     */
    @Query("SELECT w FROM Workspace w WHERE w.workspaceName LIKE %:name% AND w.isActive = true ORDER BY w.createdAt DESC")
    List<Workspace> findActiveByNameContaining(@Param("name") String name);

    /**
     * Owner ID로 Workspace 조회
     */
    @Query("SELECT w FROM Workspace w WHERE w.ownerId = :ownerId AND w.isActive = true ORDER BY w.createdAt DESC")
    List<Workspace> findActiveByOwnerId(@Param("ownerId") UUID ownerId);

    /**
     * 모든 Public Workspace 조회
     */
    @Query("SELECT w FROM Workspace w WHERE w.isPublic = true AND w.isActive = true ORDER BY w.createdAt DESC")
    List<Workspace> findAllPublicWorkspaces();

    // ============================================================================
    // 중복 체크
    // ============================================================================

    /**
     * Workspace 이름 중복 체크 (Owner 기준)
     */
    @Query("SELECT COUNT(w) > 0 FROM Workspace w WHERE w.ownerId = :ownerId AND w.workspaceName = :name AND w.isActive = true")
    boolean existsActiveByOwnerIdAndName(@Param("ownerId") UUID ownerId, @Param("name") String name);

    // ============================================================================
    // 통계
    // ============================================================================

    /**
     * 활성화된 Workspace 수
     */
    @Query("SELECT COUNT(w) FROM Workspace w WHERE w.isActive = true")
    long countActiveWorkspaces();

    /**
     * Owner별 Workspace 수
     */
    @Query("SELECT COUNT(w) FROM Workspace w WHERE w.ownerId = :ownerId AND w.isActive = true")
    long countActiveByOwnerId(@Param("ownerId") UUID ownerId);

    /**
     * 비활성화된 Workspace 조회 (관리자용)
     */
    @Query("SELECT w FROM Workspace w WHERE w.isActive = false ORDER BY w.deletedAt DESC")
    List<Workspace> findInactiveWorkspaces();

    @Query("SELECT w FROM Workspace w WHERE w.isPublic = true AND w.workspaceName LIKE %:name%")
    List<Workspace> findPublicWorkspacesByNameContaining(@Param("name") String name);
    @Query("SELECT w FROM Workspace w " +
            "WHERE w.isPublic = true " +
            "AND LOWER(w.workspaceName) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Workspace> findAllPublicWorkspacesByNameContaining(@Param("query") String query);
    // 특정 유저가 만든 워크스페이스 중 이름 검색
    List<Workspace> findByOwnerIdAndWorkspaceNameContainingIgnoreCase(UUID ownerId, String workspaceName);






}