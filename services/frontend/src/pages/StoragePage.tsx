// src/pages/StoragePage.tsx - 임시 placeholder (storage 컴포넌트 미구현)

import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';

interface StoragePageProps {
  onLogout: () => void;
}

const StoragePage: React.FC<StoragePageProps> = ({ }) => {
  const { workspaceId } = useParams<{ workspaceId: string }>();
  const navigate = useNavigate();

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center">
      <div className="text-center p-8 bg-white rounded-xl shadow-lg max-w-md">
        <div className="w-16 h-16 mx-auto mb-4 bg-blue-100 rounded-full flex items-center justify-center">
          <svg
            className="w-8 h-8 text-blue-600"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M5 8h14M5 8a2 2 0 110-4h14a2 2 0 110 4M5 8v10a2 2 0 002 2h10a2 2 0 002-2V8m-9 4h4"
            />
          </svg>
        </div>
        <h1 className="text-2xl font-bold text-gray-900 mb-2">스토리지</h1>
        <p className="text-gray-600 mb-6">
          스토리지 기능은 현재 개발 중입니다.
        </p>
        <button
          onClick={() => navigate(`/workspace/${workspaceId}`)}
          className="px-6 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
        >
          워크스페이스로 돌아가기
        </button>
      </div>
    </div>
  );
};

export default StoragePage;
