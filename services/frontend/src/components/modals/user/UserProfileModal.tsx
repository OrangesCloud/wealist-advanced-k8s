// src/components/modals/board/ProjectManageModal.tsx

/**
 * 사용자 프로필 모달 컴포넌트
 *
 * [최종 로직 목표]
 * 1. 초기 로드 시: GET /api/workspaces/all (워크스페이스 목록) + GET /api/profiles/all/me (모든 프로필)을 호출.
 * 2. 탭 선택 시: 로컬 상태(allProfiles)에서 기본 프로필(workspaceId=null)과 선택된 워크스페이스 프로필을 필터링하여 표시.
 * 3. 저장 시:
 * a. 이미지 업로드: S3에 업로드하고 Attachment 메타데이터를 저장(TEMP 상태).
 * b. 최종 프로필 업데이트: 닉네임 변경 요청 + **Attachment ID**를 사용해 최종 프로필 이미지 URL을 연결.
 */

import React, { useState, useRef, ChangeEvent, useEffect } from 'react';
import { X, Camera } from 'lucide-react';
import { useTheme } from '../../../contexts/ThemeContext';
import {
  updateMyProfile,
  getAllMyProfiles,
  getMyWorkspaces,
  uploadProfileImage, // S3 업로드 및 Attachment 저장 (TEMP)까지 처리하고 AttachmentResponse를 반환
  updateProfileImage, // Attachment ID를 사용해 최종 프로필을 업데이트하는 함수
} from '../../../api/userService';
import {
  UserProfileResponse,
  UpdateProfileRequest,
  UserWorkspaceResponse,
  AttachmentResponse, // AttachmentResponse 타입 사용
} from '../../../types/user';
import Portal from '../../common/Portal';

const DEFAULT_WORKSPACE_ID = '00000000-0000-0000-0000-000000000000';

interface UserProfileModalProps {
  onClose: () => void;
}

const UserProfileModal: React.FC<UserProfileModalProps> = ({ onClose }) => {
  const { theme } = useTheme();
  const [activeTab, setActiveTab] = useState<'default' | 'workspace'>('default');

  const [allProfiles, setAllProfiles] = useState<UserProfileResponse[]>([]);
  const [workspaces, setWorkspaces] = useState<UserWorkspaceResponse[]>([]);
  const [selectedWorkspaceId, setSelectedWorkspaceId] = useState<string>('');

  const fileInputRef = useRef<HTMLInputElement>(null);

  const [defaultNickName, setDefaultNickName] = useState('');
  const [workspaceNickName, setWorkspaceNickName] = useState('');

  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | null>(null);

  // S3에 업로드할 실제 파일 객체 상태
  const [_selectedFile, setSelectedFile] = useState<File | null>(null);

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // ========================================
  // 프로필 데이터 필터링 및 계산된 상태
  // ========================================

  const defaultProfile = allProfiles?.find((p) => p.workspaceId === DEFAULT_WORKSPACE_ID) || null;
  const currentWorkspaceProfile =
    allProfiles?.find((p) => p.workspaceId === selectedWorkspaceId) || null;

  const currentProfile =
    activeTab === 'default' ? defaultProfile : currentWorkspaceProfile || defaultProfile;

  const currentNickName = activeTab === 'default' ? defaultNickName : workspaceNickName;
  const setCurrentNickName = activeTab === 'default' ? setDefaultNickName : setWorkspaceNickName;

  // ========================================
  // 초기 데이터 로드
  // ========================================

  useEffect(() => {
    const loadInitialData = async () => {
      try {
        setLoading(true);
        const [allProfs, workspaceList] = await Promise.all([
          getAllMyProfiles(),
          getMyWorkspaces(),
        ]);

        setAllProfiles(allProfs);

        // 1. 기본 프로필 닉네임 초기화
        const initialDefaultProfile = allProfs?.find((p) => p.workspaceId === DEFAULT_WORKSPACE_ID);
        if (initialDefaultProfile) {
          setDefaultNickName(initialDefaultProfile?.nickName);
        }

        // 2. 워크스페이스 목록 초기화
        setWorkspaces(workspaceList);
        if (workspaceList.length > 0) {
          setSelectedWorkspaceId(workspaceList[0].workspaceId);
        }
      } catch (err) {
        console.error('[Initial Data Load Error]', err);
        setError('프로필 정보를 불러오는데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    };
    loadInitialData();
  }, []);

  // 워크스페이스/탭 변경 시 닉네임/아바타 상태 동기화
  useEffect(() => {
    const profileToSync = currentProfile;

    // 1. 닉네임 동기화
    if (activeTab === 'default') {
      setDefaultNickName(profileToSync?.nickName || '');
    } else if (activeTab === 'workspace') {
      setWorkspaceNickName(currentWorkspaceProfile?.nickName || defaultProfile?.nickName || '');
    }

    // 2. 아바타 미리보기 동기화
    if (!_selectedFile) {
      setAvatarPreviewUrl(profileToSync?.profileImageUrl || null);
    }
  }, [
    selectedWorkspaceId,
    activeTab,
    defaultProfile,
    currentWorkspaceProfile,
    currentProfile,
    _selectedFile,
  ]);

  // ========================================
  // 이미지 업로드 핸들러
  // ========================================

  const handleAvatarChangeClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      if (avatarPreviewUrl) {
        URL.revokeObjectURL(avatarPreviewUrl);
      }
      setAvatarPreviewUrl(URL.createObjectURL(file));
      setSelectedFile(file);
      console.log(`[File] 새 프로필 사진 선택: ${file.name}`);
    } else {
      setSelectedFile(null);
      setAvatarPreviewUrl(currentProfile?.profileImageUrl || null);
    }
  };

  // 💡 [복구된 함수] 워크스페이스 변경 핸들러
  const handleWorkspaceChange = (workspaceId: string) => {
    setSelectedWorkspaceId(workspaceId);
  };

  // ========================================
  // 저장 핸들러 (S3 업로드 및 Attachment ID 사용)
  // ========================================

  const handleSave = async () => {
    try {
      setLoading(true);
      setError(null);

      const trimmedNickName = currentNickName?.trim();

      if (!trimmedNickName) {
        setError('닉네임은 필수입니다.');
        setLoading(false);
        return;
      }

      const currentUserId = currentProfile?.userId;
      if (!currentUserId) {
        throw new Error('사용자 ID를 찾을 수 없습니다. (재로그인 필요)');
      }

      const targetWorkspaceId =
        activeTab === 'default' ? DEFAULT_WORKSPACE_ID : selectedWorkspaceId;
      let updatedProfile: UserProfileResponse | undefined = undefined;

      // 1. 이미지 업로드 처리 (새 파일이 선택된 경우)
      if (_selectedFile) {
        try {
          // uploadProfileImage는 AttachmentResponse를 반환합니다.
          const attachmentResponse: AttachmentResponse = await uploadProfileImage(
            _selectedFile,
            targetWorkspaceId,
          );
          // 💡 [Attachment ID 획득] 저장된 Attachment의 ID를 추출하여 최종 업데이트에 사용
          const attachmentId = attachmentResponse.attachmentId;

          // 2. Attachment ID를 사용하여 프로필 이미지 최종 연결 (PUT /api/profiles/me/image 호출)
          // * 이 호출이 attachmentId를 payload로 전달합니다.
          const finalProfileUpdate = await updateProfileImage(targetWorkspaceId, attachmentId);
          console.log(finalProfileUpdate);
          // updatedProfile을 최종 결과로 설정 (이 응답에는 최신 프로필 정보가 포함됨)
          updatedProfile = finalProfileUpdate;
        } catch (err) {
          console.error('[Image Upload/Link Error]', err);
          throw new Error('프로필 이미지 업데이트에 실패했습니다.');
        }
      }

      // 3. 닉네임 업데이트
      // 닉네임만 바뀌었거나, 이미지 업데이트는 했지만 닉네임은 업데이트 응답에 포함되지 않았을 경우 (또는 닉네임이 다를 경우)
      const isNickNameChanged = updatedProfile
        ? updatedProfile.nickName !== trimmedNickName
        : currentProfile?.nickName !== trimmedNickName;

      if (isNickNameChanged || !updatedProfile) {
        // updatedProfile이 null인 경우 (이미지 업데이트를 안 한 경우) 또는 닉네임 변경이 필요한 경우
        const updateData: UpdateProfileRequest = {
          nickName: trimmedNickName,
          workspaceId: targetWorkspaceId,
          userId: currentUserId,
        };
        // 닉네임 업데이트 결과로 updatedProfile을 갱신합니다.
        updatedProfile = await updateMyProfile(updateData);
      }

      // 4. updatedProfile이 최종적으로 설정되었는지 확인
      if (!updatedProfile) throw new Error('API 응답이 유효하지 않습니다.');

      // 5. 로컬 상태 업데이트 (allProfiles)
      setAllProfiles((prev) => {
        const index = prev?.findIndex((p) => p.workspaceId === targetWorkspaceId);

        const profileToUpdate: UserProfileResponse = {
          ...updatedProfile!,
          workspaceId: targetWorkspaceId,
        };

        if (index !== -1 && prev) {
          const newProfiles = [...prev];
          newProfiles[index] = profileToUpdate;
          return newProfiles;
        }
        return [...(prev || []), profileToUpdate];
      });

      // 6. 저장 후 파일 상태 초기화
      setSelectedFile(null);

      alert('✅ 프로필이 저장되었습니다!');
    } catch (err: any) {
      const errorMsg = err.response?.data?.error?.message || err.message;
      console.error('[Profile Save Error]', errorMsg);
      // 💡 오류 메시지 상세화 (BAD_REQUEST의 경우 백엔드 오류 코드를 그대로 보여줄 수 있음)
      setError(errorMsg || '프로필 저장에 실패했습니다.');
    } finally {
      setLoading(false);
    }
  };

  // ========================================
  // 모달 닫기 핸들러
  // ========================================

  const handleClose = () => {
    if (avatarPreviewUrl && _selectedFile) {
      URL.revokeObjectURL(avatarPreviewUrl);
    }
    onClose();
  };

  // ========================================
  // 렌더링
  // ========================================

  if (!defaultProfile && loading) {
    return (
      <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50">
        <div className="bg-white p-8 rounded-xl shadow-lg">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
          <p className="text-gray-700">프로필 정보를 불러오는 중...</p>
        </div>
      </div>
    );
  }

  return (
    <Portal>
      <div
        className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center p-4 z-50"
        onClick={handleClose}
      >
        <div className="relative w-full max-w-md" onClick={(e) => e.stopPropagation()}>
          <div
            className={`relative ${theme.colors.card} ${theme.effects.borderWidth} ${theme.colors.border} ${theme.effects.borderRadius} shadow-xl`}
          >
            {/* 헤더 */}
            <div className="flex items-center justify-between p-4 pb-3">
              <h2 className={`${theme.font.size.base} font-bold text-gray-800`}>
                사용자 프로필 설정
              </h2>
              <button
                onClick={handleClose}
                className="p-2 hover:bg-gray-100 rounded-lg transition"
                title="닫기"
              >
                <X className="w-4 h-4 text-gray-600" />
              </button>
            </div>

            {/* 탭 메뉴 */}
            <div className="flex border-b border-gray-200 px-6">
              <button
                onClick={() => setActiveTab('default')}
                className={`flex-1 py-3 text-sm font-medium transition-colors relative ${
                  activeTab === 'default' ? 'text-blue-600' : 'text-gray-500 hover:text-gray-700'
                }`}
              >
                기본 프로필
                {activeTab === 'default' && (
                  <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-blue-600"></div>
                )}
              </button>
              <button
                onClick={() => setActiveTab('workspace')}
                className={`flex-1 py-3 text-sm font-medium transition-colors relative ${
                  activeTab === 'workspace' ? 'text-blue-600' : 'text-gray-500 hover:text-gray-700'
                }`}
              >
                워크스페이스별 프로필
                {activeTab === 'workspace' && (
                  <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-blue-600"></div>
                )}
              </button>
            </div>

            {/* 탭 컨텐츠 */}
            <div className="p-6 space-y-5">
              {/* 에러 메시지 */}
              {error && (
                <div className="p-3 bg-red-100 border border-red-400 text-red-700 rounded-md text-sm">
                  {error}
                </div>
              )}

              {/* 워크스페이스 선택 */}
              <div className={activeTab === 'default' ? 'hidden' : ''}>
                <label className={`block ${theme.font.size.xs} mb-2 text-gray-500 font-medium`}>
                  워크스페이스 선택:
                </label>
                <select
                  value={selectedWorkspaceId}
                  onChange={(e) => handleWorkspaceChange(e.target.value)}
                  className={`w-full px-3 py-2 ${theme.effects.cardBorderWidth} ${theme.colors.border} ${theme.colors.card} ${theme.font.size.xs} ${theme.effects.borderRadius} focus:outline-none focus:ring-2 focus:ring-blue-500`}
                  disabled={workspaces.length === 0}
                >
                  {workspaces.map((workspace) => (
                    <option key={workspace.workspaceId} value={workspace.workspaceId}>
                      {workspace.workspaceName}
                    </option>
                  ))}
                </select>
                <p className="mt-1 text-xs text-gray-500">
                  워크스페이스마다 다른 프로필을 설정할 수 있습니다
                </p>
              </div>
              {/* 기본 탭일 때 높이 유지를 위한 공간 */}
              {activeTab === 'default' && <div style={{ height: '70px' }} className="w-full"></div>}

              {/* 프로필 이미지 */}
              <div className="flex flex-col items-center mb-4">
                <div className="relative">
                  {avatarPreviewUrl ? (
                    <img
                      src={_selectedFile ? avatarPreviewUrl : currentProfile?.profileImageUrl || ''}
                      alt="프로필 미리보기"
                      className="w-24 h-24 object-cover border-2 border-gray-300 rounded-full"
                    />
                  ) : (
                    <div className="w-24 h-24 bg-blue-500 border-2 border-gray-300 flex items-center justify-center text-white text-3xl font-bold rounded-full">
                      {currentNickName[0] || 'U'}
                    </div>
                  )}

                  <input
                    type="file"
                    ref={fileInputRef}
                    onChange={handleFileChange}
                    accept="image/*"
                    className="hidden"
                  />

                  <button
                    onClick={handleAvatarChangeClick}
                    className="absolute bottom-0 right-0 p-2 bg-gray-700 hover:bg-gray-800 text-white rounded-full transition shadow-md"
                    title="프로필 사진 변경"
                  >
                    <Camera className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* 닉네임 */}
              <div>
                <label className={`block ${theme.font.size.xs} mb-2 text-gray-500 font-medium`}>
                  닉네임:
                </label>
                <input
                  type="text"
                  value={currentNickName}
                  onChange={(e) => setCurrentNickName(e.target.value)}
                  className={`w-full px-3 py-2 ${theme.effects.cardBorderWidth} ${theme.colors.border} ${theme.colors.card} ${theme.font.size.xs} ${theme.effects.borderRadius} focus:outline-none focus:ring-2 focus:ring-blue-500`}
                  placeholder="닉네임을 입력하세요"
                />
              </div>

              {/* 버튼 영역 */}
              <div className="flex gap-2 pt-4">
                <button
                  onClick={handleSave}
                  disabled={loading}
                  className={`flex-1 ${theme.colors.primary} text-white py-3 ${
                    theme.effects.borderRadius
                  } font-semibold transition ${
                    loading ? 'opacity-50 cursor-not-allowed' : 'hover:opacity-90'
                  }`}
                >
                  {loading ? '저장 중...' : '저장'}
                </button>
                <button
                  onClick={handleClose}
                  className="flex-1 bg-gray-300 text-gray-800 py-3 rounded-lg font-semibold hover:bg-gray-400 transition"
                >
                  취소
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Portal>
  );
};

export default UserProfileModal;
