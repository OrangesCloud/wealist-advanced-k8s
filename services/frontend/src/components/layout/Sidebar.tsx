import React from 'react';
import { useNavigate } from 'react-router-dom';
import { Home, MessageSquare, Bell, File } from 'lucide-react';
import { useTheme } from '../../contexts/ThemeContext';
import type { UserProfileResponse, WorkspaceMemberResponse } from '../../types/user';

interface SidebarProps {
  workspaceId: string;
  userProfile: UserProfileResponse | null;
  isChatActive: boolean;
  onChatToggle: () => void;
  onUserMenuToggle: () => void;
  onStartChat?: (member: WorkspaceMemberResponse) => Promise<void>;
  totalUnreadCount?: number; // 🔥 총 읽지 않은 메시지 수
}

export const Sidebar: React.FC<SidebarProps> = ({
  workspaceId,
  userProfile,
  isChatActive,
  onChatToggle,
  onUserMenuToggle,
  onStartChat,
  totalUnreadCount = 0,
}) => {
  const navigate = useNavigate();
  const { theme } = useTheme();

  const sidebarWidth = 'w-16 sm:w-20';

  const handleBackToSelect = () => {
    navigate('/workspaces');
  };

  return (
    <aside
      className={`${sidebarWidth} fixed top-0 left-0 h-full flex flex-col justify-between ${theme.colors.primary} text-white shadow-xl z-50 flex-shrink-0`}
    >
      <div className="flex flex-col flex-grow items-center">
        {/* 워크스페이스 로고 */}
        <div className="py-3 flex justify-center w-full relative">
          <button
            onClick={handleBackToSelect}
            title="워크스페이스 목록으로"
            className="w-12 h-12 rounded-lg mx-auto flex items-center justify-center text-xl font-bold transition bg-white text-blue-800 ring-2 ring-white/50 hover:bg-gray-100"
          >
            {workspaceId.slice(0, 1).toUpperCase()}
          </button>
        </div>

        {/* 사이드바 메뉴 */}
        <div className="flex flex-col gap-2 mt-4 flex-grow px-2 w-full pt-4">
          <button
            className="w-12 h-12 rounded-lg mx-auto flex items-center justify-center transition bg-blue-600 text-white ring-2 ring-white/50"
            title="홈"
          >
            <Home className="w-6 h-6" />
          </button>

          {/* 채팅 버튼 */}
          <div className="relative mx-auto">
            <button
              onClick={onChatToggle}
              className={`w-12 h-12 rounded-lg flex items-center justify-center transition ${
                isChatActive
                  ? 'bg-blue-600 text-white ring-2 ring-white/50'
                  : 'hover:bg-blue-600/50 text-white/80 ring-1 ring-white/20'
              }`}
              title="채팅"
            >
              <MessageSquare className="w-6 h-6" />
            </button>
            {/* 🔥 읽지 않은 메시지 알림 배지 */}
            {totalUnreadCount > 0 && !isChatActive && (
              <div className="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 bg-red-500 text-white text-xs rounded-full flex items-center justify-center font-bold ring-2 ring-gray-800">
                {totalUnreadCount > 9 ? '9+' : totalUnreadCount}
              </div>
            )}
          </div>

          <button
            className="w-12 h-12 rounded-lg mx-auto flex items-center justify-center bg-gray-700 hover:bg-gray-600 text-white opacity-50 transition"
            title="알림"
          >
            <Bell className="w-6 h-6" />
          </button>
          <button
            className="w-12 h-12 rounded-lg mx-auto flex items-center justify-center bg-gray-700 hover:bg-gray-600 text-white opacity-50 transition"
            title="파일"
          >
            <File className="w-6 h-6" />
          </button>
        </div>
      </div>

      {/* 하단 유저 메뉴 버튼 */}
      <div className={`py-3 px-2 border-t border-gray-700`}>
        <button
          onClick={(e) => {
            e.stopPropagation();
            onUserMenuToggle();
          }}
          className={`w-full flex items-center justify-center py-2 text-sm rounded-lg hover:bg-blue-600 transition relative`}
          title="계정 메뉴"
        >
          {/* 💡 relative 컨테이너로 감싸서 온라인 인디케이터 배치 */}
          <div className="relative">
            <div
              className={`w-10 h-10 rounded-full bg-gray-300 flex items-center justify-center text-sm font-bold ring-2 ring-white/50 text-gray-700 overflow-hidden`}
            >
              {userProfile?.profileImageUrl ? (
                <img
                  src={userProfile.profileImageUrl}
                  alt={userProfile.nickName}
                  className="w-full h-full object-cover"
                />
              ) : (
                userProfile?.nickName[0]?.toUpperCase() || '나'
              )}
            </div>
            {/* 💡 온라인 상태 인디케이터 */}
            <div className="absolute bottom-0 right-0 w-3 h-3 bg-green-500 rounded-full ring-2 ring-white"></div>
          </div>
        </button>
      </div>
    </aside>
  );
};
