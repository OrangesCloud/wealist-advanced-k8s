package OrangeCloud.UserRepo.repository;

import OrangeCloud.UserRepo.entity.WorkspaceJoinRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceJoinRequestRepository extends JpaRepository<WorkspaceJoinRequest, UUID> {

    // ============================================================================
    // 기본 조회
    // ============================================================================

    /**
     * 특정 Workspace의 모든 가입 신청 목록 조회
     */
    List<WorkspaceJoinRequest> findByWorkspaceId(UUID workspaceId);

    /**
     * 특정 Workspace와 특정 User의 가입 신청 목록 조회
     * (요청하신 메서드: WorkspaceService에서 PENDING 요청을 찾기 위해 사용)
     */
    List<WorkspaceJoinRequest> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
    
    // ============================================================================
    // 상태별 조회 (비즈니스 로직에 필수)
    // ============================================================================

    /**
     * 특정 Workspace의 상태별 가입 신청 목록 조회
     * (Service의 getJoinRequests에서 사용)
     */
    List<WorkspaceJoinRequest> findByWorkspaceIdAndStatus(UUID workspaceId, WorkspaceJoinRequest.JoinRequestStatus status);

    /**
     * 특정 Workspace에 대한 특정 사용자의 특정 상태 가입 신청 조회
     * (Service의 approve/rejectJoinRequest에서 PENDING 요청을 정확히 찾기 위해 사용)
     */
    Optional<WorkspaceJoinRequest> findByWorkspaceIdAndUserIdAndStatus(
            UUID workspaceId, 
            UUID userId, 
            WorkspaceJoinRequest.JoinRequestStatus status
    );

    /**
     * 특정 Workspace에 대한 특정 사용자의 활성(PENDING) 가입 신청이 있는지 확인
     * (Service의 createJoinRequest에서 중복 신청을 막기 위해 사용)
     */
    boolean existsByWorkspaceIdAndUserIdAndStatus(
            UUID workspaceId, 
            UUID userId, 
            WorkspaceJoinRequest.JoinRequestStatus status
    );
    List<WorkspaceJoinRequest> findByUserIdAndStatusIn(UUID userId, List<WorkspaceJoinRequest.JoinRequestStatus> statuses);

}