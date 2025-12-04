import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTheme } from '../contexts/ThemeContext';
import { useAuth } from '../contexts/AuthContext';
// 💡 [수정] searchWorkspaces, createJoinRequest 함수 import 추가
import {
  getMyWorkspaces,
  createWorkspace,
  createJoinRequest,
  getPublicWorkspaces,
  inviteUser,
} from '../api/userService';
import { Search, Plus, X, AlertCircle, Settings, LogOut } from 'lucide-react';
import {
  CreateWorkspaceRequest,
  WorkspaceResponse,
  JoinRequestResponse,
  UserWorkspaceResponse,
} from '../types/user';
import WorkspaceManagementModal from '../components/modals/user/wsManager/WorkspaceManagementModal';

type WorkspacePageStep = 'list' | 'create-form' | 'add-members' | 'loading';

interface PendingMember {
  id: string;
  email: string;
}

const SelectWorkspacePage: React.FC = () => {
  const navigate = useNavigate();
  const { theme } = useTheme();
  const { userEmail, logout, nickName } = useAuth();

  // 페이지 상태
  const [step, setStep] = useState<WorkspacePageStep>('list');
  const [workspaces, setWorkspaces] = useState<UserWorkspaceResponse[] | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');

  // 💡 [NEW] 검색 상태
  const [searchedWorkspaces, setSearchedWorkspaces] = useState<WorkspaceResponse[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  // 폼 상태
  const [newWorkspaceName, setNewWorkspaceName] = useState('');
  const [newDescription, setNewDescription] = useState('');

  // 멤버 초대
  const [pendingMembers, setPendingMembers] = useState<PendingMember[]>([]);
  const [memberEmail, setMemberEmail] = useState('');
  const [memberEmailError, setMemberEmailError] = useState<string | null>(null);

  const [_createdWorkspaceId, setCreatedWorkspaceId] = useState<string | null>(null);

  // 워크스페이스 관리 모달
  const [managingWorkspace, setManagingWorkspace] = useState<UserWorkspaceResponse | null>(null);

  // 1. 초기 워크스페이스 로드/새로고침 함수
  const fetchWorkspaces = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const fetchedWorkspaces = await getMyWorkspaces();
      setWorkspaces(fetchedWorkspaces);
      return fetchedWorkspaces;
    } catch (e: any) {
      const err = e as Error;
      console.error('워크스페이스 목록 조회 실패:', err);
      // 인터셉터가 401 처리를 담당하므로, 이 에러는 주로 서버/네트워크 오류입니다.
      setError(`워크스페이스 목록 조회 실패: ${err.message}`);
      setWorkspaces([]);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchWorkspaces();
  }, [navigate]);

  // 💡 [NEW] 검색 API 실행 함수
  const executeSearch = useCallback(
    async (query: string) => {
      if (!query.trim()) {
        setSearchedWorkspaces([]);
        return;
      }

      setIsSearching(true);
      setError(null);
      try {
        const results = await getPublicWorkspaces(query);

        // 내 워크스페이스에 이미 속한 항목 제외
        const myIds = new Set(workspaces?.map((w) => w.workspaceId) || []);
        const filteredResults = results.filter((r) => !myIds.has(r.workspaceId));

        setSearchedWorkspaces(filteredResults);
      } catch (e: any) {
        console.error('❌ 워크스페이스 검색 실패:', e);
        setSearchedWorkspaces([]);
        setError('검색 중 오류가 발생했습니다.');
      } finally {
        setIsSearching(false);
      }
    },
    [workspaces],
  );

  // 💡 [NEW] Enter 또는 버튼 클릭 시 검색 실행 핸들러
  const handleSearchSubmit = () => {
    executeSearch(searchQuery);
  };

  const handleSearchKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      handleSearchSubmit();
    }
  };

  // 4. 이메일 유효성 검사 (동일)
  const isValidEmail = (email: string) => {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
  };

  // 5. 멤버 추가/제거 (동일)
  const handleAddMember = () => {
    setMemberEmailError(null);
    if (!memberEmail.trim()) {
      setMemberEmailError('이메일을 입력해주세요');
      return;
    }
    if (!isValidEmail(memberEmail)) {
      setMemberEmailError('유효한 이메일 주소를 입력해주세요');
      return;
    }
    if (pendingMembers.some((m) => m.email === memberEmail)) {
      setMemberEmailError('이미 추가된 이메일입니다');
      return;
    }
    setPendingMembers([...pendingMembers, { id: Date.now().toString(), email: memberEmail }]);
    setMemberEmail('');
  };

  const handleRemoveMember = (id: string) => {
    setPendingMembers(pendingMembers.filter((m) => m.id !== id));
  };

  // 6. 워크스페이스 생성 (동일)
  const handleCreateWorkspaceWithMembers = async () => {
    if (!newWorkspaceName.trim()) {
      setError('워크스페이스 이름을 입력해주세요');
      return;
    }
    setIsLoading(true);
    setError(null);
    try {
      // 1️⃣ 워크스페이스 생성
      const createData: CreateWorkspaceRequest = {
        workspaceName: newWorkspaceName,
        workspaceDescription: newDescription || '-',
      };

      const newWorkspace = await createWorkspace(createData);

      if (!newWorkspace || !newWorkspace.workspaceId) {
        throw new Error(
          '워크스페이스가 생성되었지만, 응답에서 유효한 Workspace ID를 찾을 수 없습니다. (응답 구조 오류)',
        );
      }

      const newWorkspaceId = newWorkspace.workspaceId;
      setCreatedWorkspaceId(newWorkspaceId);

      // 2️⃣ 멤버 초대 (pendingMembers가 있을 경우)
      if (pendingMembers.length > 0) {
        console.log(`${pendingMembers.length}명의 멤버 초대를 시작합니다...`);

        // 병렬로 모든 초대 요청 실행
        const invitePromises = pendingMembers.map((member) =>
          inviteUser(newWorkspaceId, member.email).catch((err) => {
            console.error(`❌ ${member.email} 초대 실패:`, err);
            return null; // 실패해도 다른 초대는 계속 진행
          }),
        );

        const results = await Promise.all(invitePromises);
        const successCount = results.filter((r) => r !== null).length;
        const failCount = pendingMembers.length - successCount;

        if (failCount > 0) {
          alert(
            `워크스페이스 '${newWorkspaceName}' 생성 완료!\n` +
              `${successCount}명 초대 성공, ${failCount}명 초대 실패`,
          );
        } else {
          alert(
            `워크스페이스 '${newWorkspaceName}' 생성 완료!\n` +
              `${successCount}명의 멤버 초대 완료!`,
          );
        }
      } else {
        alert(`워크스페이스 '${newWorkspaceName}' 생성 완료!`);
      }

      resetCreateForm();
      // 💡 [수정] 생성 시 역할(role)은 OWNER로 가정하고 navigate state로 전달
      navigate(`/workspace/${newWorkspaceId}`, { state: { userRole: 'OWNER' } });
    } catch (e: any) {
      const err = e as Error;
      setError(`워크스페이스 생성 실패: ${err.message}`);
      setIsLoading(false);
    }
  };

  // 7. 기존 워크스페이스 선택 (동일)
  const handleSelectExistingWorkspace = async (workspace: UserWorkspaceResponse) => {
    if (workspace?.role == 'PENDING') return;

    setIsLoading(true);
    setError(null);
    try {
      // 💡 [수정] navigate state로 해당 워크스페이스의 role 전달
      alert(`워크스페이스 '${workspace.workspaceName}'에 참여 완료!`);
      navigate(`/workspace/${workspace.workspaceId}`, { state: { userRole: workspace.role } });
    } catch (e: any) {
      const err = e as Error;
      setError(`워크스페이스 참여 실패: ${err.message}`);
      setIsLoading(false);
    }
  };

  // 8. [NEW] 검색된 워크스페이스 가입 요청
  const handleJoinRequest = async (workspace: WorkspaceResponse) => {
    setIsLoading(true);
    setError(null);
    try {
      // 💡 API 호출: POST /api/workspaces/join-requests
      const joinRequest: JoinRequestResponse = await createJoinRequest(workspace.workspaceId);

      setSearchQuery(''); // 검색 결과 초기화
      setSearchedWorkspaces([]);

      // 내 워크스페이스 목록 갱신 (가입 요청 상태가 반영된 목록을 가져옵니다)
      await fetchWorkspaces();

      alert(`'${workspace.workspaceName}'에 가입 요청을 보냈습니다. (${joinRequest.status})`);
    } catch (e: any) {
      const errorMsg = e.response?.data?.error?.message || e.message;
      console.error('❌ 가입 요청 실패:', errorMsg);
      setError(`가입 요청 실패: ${errorMsg}`);
    } finally {
      setIsLoading(false);
    }
  };

  // 9. 워크스페이스 관리 모달 열기 (동일)
  const handleManageWorkspace = (workspace: UserWorkspaceResponse) => {
    setManagingWorkspace(workspace);
  };

  // 10. 폼 초기화 (동일)
  const resetCreateForm = () => {
    setNewWorkspaceName('');
    setNewDescription('');
    setPendingMembers([]);
    setMemberEmail('');
    setMemberEmailError(null);
    setStep('list');
  };

  // --- 로딩 화면 (동일) ---
  if (isLoading && workspaces === null) {
    return (
      <div
        className={`min-h-screen ${theme.colors.background} flex items-center justify-center p-4`}
      >
        <div className="p-8">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
          <p className={`${theme.font.size.lg} ${theme.colors.text}`}>
            워크스페이스 정보를 확인 중...
          </p>
        </div>
      </div>
    );
  }

  // --- 메인 렌더링 ---
  return (
    <div className={`min-h-screen ${theme.colors.background} flex items-center justify-center p-4`}>
      <div
        className={`${theme.colors.card} ${theme.effects.borderRadius} p-6 sm:p-8 w-full max-w-2xl relative z-10 shadow-xl ${theme.effects.cardBorderWidth} ${theme.colors.border}`}
      >
        {/* 사용자 정보 헤더 */}
        <div className="flex items-center justify-between mb-6 pb-4">
          <div className="flex items-center gap-2">
            <span className={`${theme.font.size.sm} ${theme.colors.text}`}>
              {userEmail ? `반갑습니다, ${nickName}님!` : '환영합니다!'}
            </span>
          </div>
          <button
            onClick={logout}
            className="flex items-center gap-2 px-3 py-2 text-gray-600 hover:text-red-600 hover:bg-red-50 rounded-lg transition"
            title="로그아웃"
          >
            <LogOut className="w-4 h-4" />
            <span className={`${theme.font.size.sm}`}>로그아웃</span>
          </button>
        </div>

        {/* Step 1: 워크스페이스 목록 & 선택 */}
        {step === 'list' && (
          <>
            <h2
              className={`text-center ${theme.font.size.xl} font-extrabold ${theme.colors.text} mb-2`}
            >
              워크스페이스 선택
            </h2>
            <p className={`text-center mb-6 ${theme.font.size.sm} ${theme.colors.subText}`}>
              기존 워크스페이스에 참여하거나 새로운 워크스페이스를 생성하세요.
            </p>

            {error && (
              <div
                className={`${theme.colors.danger} text-center mb-4 ${theme.font.size.sm} border border-red-300 p-2 rounded-md bg-red-50 flex items-center gap-2`}
              >
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                {error}
              </div>
            )}
            {/* 검색 입력 필드 */}
            <div className="relative mb-4">
              <input
                type="text"
                placeholder="워크스페이스 이름 검색"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                onKeyDown={handleSearchKeyDown} // 💡 [추가] 엔터 키 이벤트 핸들러
                className={`w-full px-4 py-3 ${theme.colors.secondary} ${theme.font.size.sm} rounded-lg border-2 border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition`}
              />
              {/* 검색 버튼 (클릭 시 실행) */}
              <button
                onClick={handleSearchSubmit}
                className="absolute right-0 top-0 h-full px-4 text-gray-500 hover:text-blue-500 transition"
                title="검색 실행"
                disabled={isSearching}
              >
                <Search className="w-4 h-4" />
              </button>
            </div>

            {/* 💡 [NEW] 검색된 워크스페이스 목록 (가입 요청 목록) */}
            {(searchedWorkspaces?.length > 0 || isSearching) && (
              <div className={`mb-4 border-2 ${theme.colors.border} rounded-lg shadow-md`}>
                <h3 className="p-3 bg-gray-100 font-semibold text-sm rounded-t-lg">
                  {isSearching ? '검색 중...' : `검색 결과 (${searchedWorkspaces?.length}개)`}
                </h3>
                <div className={`max-h-40 overflow-y-auto`}>
                  {searchedWorkspaces?.map((ws) => (
                    <div
                      key={ws.workspaceId}
                      // 클릭 시 가입 요청 핸들러 호출
                      onClick={() => !isLoading && handleJoinRequest(ws)}
                      className={`w-full text-left p-3 hover:bg-green-50 border-b border-gray-100 transition flex justify-between items-center cursor-pointer`}
                    >
                      <div>
                        <span className="font-semibold text-gray-800">{ws.workspaceName}</span>
                        <p className={`text-gray-500 ${theme.font.size.xs}`}>
                          {ws.workspaceDescription}
                        </p>
                      </div>
                      <span className="text-xs text-green-600 border border-green-300 px-2 py-1 rounded">
                        가입 요청
                      </span>
                    </div>
                  ))}
                  {searchedWorkspaces.length === 0 && !isSearching && searchQuery.trim() && (
                    <p className="p-3 text-center text-sm text-gray-500">
                      검색된 워크스페이스가 없습니다.
                    </p>
                  )}
                </div>
              </div>
            )}

            <div className="mb-2">
              <h3 className="font-semibold text-sm text-gray-700 mb-2">
                나의 워크스페이스 ({workspaces?.length || 0}개)
              </h3>
            </div>
            <div
              className={`max-h-60 overflow-y-auto border-2 ${theme.colors.border} rounded-lg mb-4`}
            >
              {workspaces && workspaces?.length > 0 ? (
                workspaces?.map((ws) => (
                  <div
                    key={ws.workspaceId}
                    aria-disabled={ws?.role === 'PENDING'}
                    onClick={() => !isLoading && handleSelectExistingWorkspace(ws)}
                    className={`w-full text-left p-4 hover:bg-blue-50 border-b border-gray-100 ${
                      theme.colors.text
                    } ${
                      theme.font.size.sm
                    } transition flex justify-between items-center last:border-b-0 ${
                      isLoading
                        ? 'opacity-50 cursor-not-allowed'
                        : ws?.role === 'PENDING'
                        ? 'bg-gray-50 text-gray-400 cursor-not-allowed pointer-events-none'
                        : 'hover:bg-blue-50 cursor-pointer'
                    }`}
                  >
                    <div>
                      <span className="font-semibold">{ws.workspaceName}</span>
                      <p className={`${theme.colors.subText} ${theme.font.size.xs}`}>
                        {ws?.workspaceDescription}
                      </p>
                    </div>
                    <div className="flex items-center gap-2">
                      {ws?.owner && (
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            handleManageWorkspace(ws);
                          }}
                          className="p-2 hover:bg-gray-200 rounded-lg transition"
                          title="워크스페이스 관리"
                        >
                          <Settings className="w-4 h-4 text-gray-600" />
                        </button>
                      )}
                      <span
                        className={`${theme.colors.info} ${theme.font.size.xs} px-2 py-1 border border-blue-200 rounded`}
                      >
                        {ws?.role === 'PENDING' ? '승인 대기' : '선택'}
                      </span>
                    </div>
                  </div>
                ))
              ) : (
                <p className={`p-4 text-center ${theme.colors.subText} ${theme.font.size.sm}`}>
                  소속된 워크스페이스가 없습니다.
                </p>
              )}
            </div>

            {/* 새 워크스페이스 생성 버튼 */}
            <button
              onClick={() => setStep('create-form')}
              disabled={isLoading}
              className={`w-full ${theme.colors.primary} text-white py-3 font-bold rounded-lg ${theme.colors.primaryHover} transition disabled:opacity-50 shadow-lg flex items-center justify-center gap-2`}
            >
              <Plus className="w-5 h-5" /> 새 워크스페이스 생성
            </button>
          </>
        )}

        {/* Step 2: 워크스페이스 정보 입력 (동일) */}
        {step === 'create-form' && (
          <>
            <h2
              className={`text-center ${theme.font.size.xl} font-extrabold ${theme.colors.text} mb-2`}
            >
              새로운 워크스페이스 생성
            </h2>
            <p className={`text-center mb-6 ${theme.font.size.sm} ${theme.colors.subText}`}>
              워크스페이스 정보를 입력하세요.
            </p>

            {error && (
              <div
                className={`${theme.colors.danger} text-center mb-4 ${theme.font.size.sm} border border-red-300 p-2 rounded-md bg-red-50`}
              >
                {error}
              </div>
            )}

            <div className="space-y-4 mb-6">
              <div>
                <label
                  className={`block ${theme.font.size.sm} font-semibold ${theme.colors.text} mb-2`}
                >
                  워크스페이스 이름 *
                </label>
                <input
                  type="text"
                  placeholder="예: Orange Cloud 팀"
                  value={newWorkspaceName}
                  onChange={(e) => setNewWorkspaceName(e.target.value)}
                  className={`w-full px-4 py-3 ${theme.colors.secondary} ${theme.font.size.sm} rounded-lg border-2 border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition`}
                  disabled={isLoading}
                />
              </div>

              <div>
                <label
                  className={`block ${theme.font.size.sm} font-semibold ${theme.colors.text} mb-2`}
                >
                  설명 (선택)
                </label>
                <input
                  type="text"
                  placeholder="예: Orange Cloud 프로젝트"
                  value={newDescription}
                  onChange={(e) => setNewDescription(e.target.value)}
                  className={`w-full px-4 py-3 ${theme.colors.secondary} ${theme.font.size.sm} rounded-lg border-2 border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition`}
                  disabled={isLoading}
                />
              </div>
            </div>

            <div className="flex gap-3">
              <button
                onClick={() => resetCreateForm()}
                disabled={isLoading}
                className={`flex-1 ${theme.colors.secondary} text-gray-700 py-3 font-bold rounded-lg border-2 ${theme.colors.border} hover:bg-gray-100 transition disabled:opacity-50`}
              >
                ← 돌아가기
              </button>
              <button
                onClick={() => setStep('add-members')}
                disabled={isLoading || !newWorkspaceName.trim()}
                className={`flex-1 ${theme.colors.primary} text-white py-3 font-bold rounded-lg ${theme.colors.primaryHover} transition disabled:opacity-50`}
              >
                다음: 멤버 초대 →
              </button>
            </div>
          </>
        )}

        {/* Step 3: 멤버 초대 (동일) */}
        {step === 'add-members' && (
          <>
            <h2
              className={`${theme.font.size.xl} font-extrabold ${theme.colors.text} mb-2 text-center`}
            >
              멤버 초대 (선택사항)
            </h2>
            <p className={`text-center mb-6 ${theme.font.size.sm} ${theme.colors.subText}`}>
              초대할 멤버의 이메일을 입력하세요. 나중에 추가할 수도 있습니다.
            </p>

            {error && (
              <div
                className={`${theme.colors.danger} text-center mb-4 ${theme.font.size.sm} border border-red-300 p-2 rounded-md bg-red-50`}
              >
                {error}
              </div>
            )}

            {/* 이메일 입력 폼 */}
            <div className="mb-4 space-y-2 w-full">
              <div className="flex gap-2 w-full">
                <input
                  type="email"
                  placeholder="멤버 이메일"
                  value={memberEmail}
                  onChange={(e) => setMemberEmail(e.target.value)}
                  onKeyPress={(e) => e.key === 'Enter' && handleAddMember()}
                  className={`w-full px-4 py-3 ${theme.colors.secondary} ${theme.font.size.sm} rounded-lg border-2 border-gray-300 focus:border-blue-500 focus:ring-2 focus:ring-blue-200 transition`}
                  disabled={isLoading}
                />
                <button
                  onClick={handleAddMember}
                  disabled={isLoading || !memberEmail.trim()}
                  className={`px-4 py-3 font-bold rounded-lg flex-shrink-0 transition ${
                    isLoading || !memberEmail.trim()
                      ? 'bg-blue-300 text-white cursor-not-allowed opacity-60'
                      : 'bg-blue-500 text-white hover:bg-blue-600'
                  }`}
                >
                  <Plus className="w-5 h-5" />
                </button>
              </div>
              {memberEmailError && (
                <p className={`${theme.font.size.xs} ${theme.colors.danger}`}>{memberEmailError}</p>
              )}
            </div>

            {/* 추가된 멤버 목록 (동일) */}
            {pendingMembers?.length > 0 && (
              <div className="mb-6 p-4 bg-blue-50 rounded-lg border border-blue-200">
                <p className={`${theme.font.size.sm} font-semibold ${theme.colors.text} mb-3`}>
                  초대 예정 멤버 ({pendingMembers?.length}명)
                </p>
                <div className="space-y-2">
                  {pendingMembers?.map((member) => (
                    <div
                      key={member.id}
                      className="flex items-center justify-between bg-white p-2 rounded border border-gray-200"
                    >
                      <span className={`${theme.font.size.sm} ${theme.colors.text}`}>
                        {member.email}
                      </span>
                      <button
                        onClick={() => handleRemoveMember(member.id)}
                        className="text-red-500 hover:text-red-700 transition"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* 액션 버튼 (동일) */}
            <div className="flex gap-3">
              <button
                onClick={() => setStep('create-form')}
                disabled={isLoading}
                className={`flex-1 ${theme.colors.secondary} text-gray-700 py-3 font-bold rounded-lg border-2 ${theme.colors.border} hover:bg-gray-100 transition disabled:opacity-50`}
              >
                ← 이전
              </button>
              <button
                onClick={handleCreateWorkspaceWithMembers}
                disabled={isLoading || !newWorkspaceName.trim()}
                className={`flex-1 ${theme.colors.success} text-white py-3 font-bold rounded-lg hover:bg-green-600 transition disabled:opacity-50`}
              >
                {isLoading ? '생성 중...' : '워크스페이스 생성 완료'}
              </button>
            </div>
          </>
        )}
      </div>

      {/* 워크스페이스 관리 모달 */}
      {managingWorkspace && (
        <WorkspaceManagementModal
          workspaceId={managingWorkspace.workspaceId}
          workspaceName={managingWorkspace.workspaceName}
          onClose={() => setManagingWorkspace(null)}
        />
      )}
    </div>
  );
};

export default SelectWorkspacePage;
