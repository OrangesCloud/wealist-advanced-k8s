package OrangeCloud.UserRepo.repository;

import OrangeCloud.UserRepo.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    // ============================================================================
    // 기본 멤버 조회
    // ============================================================================

    /**
     * 특정 Workspace에 속한 모든 활성화된 멤버 조회
     */
    List<WorkspaceMember> findAllByWorkspaceIdAndIsActiveTrue(UUID workspaceId);

    /**
     * 특정 Workspace의 모든 멤버 조회 (isActive에 상관없이)
     */
    List<WorkspaceMember> findAllByWorkspaceId(UUID workspaceId);

    /**
     * 특정 사용자 ID로 모든 활성화된 멤버십 조회
     */
    List<WorkspaceMember> findActiveByUserId(UUID userId); // Service에서 이미 사용 중

    /**
     * Workspace ID와 User ID로 특정 멤버십 조회
     */
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    /**
     * 멤버 존재 여부 확인 (권한 확인용)
     */
    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId); // Service에서 이미 사용 중

    // ============================================================================
    // OWNER 조회 (커스텀 쿼리)
    // ============================================================================

    /**
     * 특정 Workspace의 OWNER 멤버 조회
     */
    @Query("SELECT wm FROM WorkspaceMember wm WHERE wm.workspaceId = :workspaceId AND wm.role = 'OWNER' AND wm.isActive = true")
    Optional<WorkspaceMember> findOwnerByWorkspaceId(@Param("workspaceId") UUID workspaceId);

    // ============================================================================
    // 기본 Workspace 설정
    // ============================================================================

    /**
     * 특정 사용자의 기본(Default) Workspace 멤버십 조회
     */

    boolean existsByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);



}