import React, { Suspense, lazy } from 'react';
import { ThemeProvider } from './contexts/ThemeContext'; // ✅
import { AuthProvider } from './contexts/AuthContext'; // ✅
// 1. react-router-dom에서 필요한 것들을 임포트합니다.
import { Routes, Route, Navigate, Outlet, useNavigate } from 'react-router-dom';

// Lazy load 페이지들 (이름 일관성 유지)
const AuthPage = lazy(() => import('./pages/Authpage'));
const SelectWorkspacePage = lazy(() => import('./pages/SelectWorkspacePage'));
const MainDashboard = lazy(() => import('./pages/MainDashboard'));
const OAuthRedirectPage = lazy(() => import('./pages/OAuthRedirectPage'));

const LoadingScreen = ({ msg = '로딩 중..' }) => (
  <div className="text-center min-h-screen flex items-center justify-center bg-gray-50">
    <div className="p-8 bg-white rounded-xl shadow-lg">
      <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500 mx-auto mb-4"></div>
      <h1 className="text-xl font-medium text-gray-800">{msg}</h1>
    </div>
  </div>
);

// 2. 인증이 필요한 페이지를 감싸는 '보호 라우트' 컴포넌트
const ProtectedRoute = () => {
  const accessToken = localStorage.getItem('accessToken');
  // 토큰이 없으면 로그인 페이지로 리다이렉트
  if (!accessToken) {
    return <Navigate to="/" replace />;
  }
  // 토큰이 있으면 자식 컴포넌트(SelectWorkspacePage 또는 MainDashboard)를 렌더링
  return <Outlet />;
};

const App: React.FC = () => {
  // 4. [신규] MainDashboard로 전달할 로그아웃 핸들러 생성
  const navigate = useNavigate();

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('nickName');
    localStorage.removeItem('userEmail');
    // 로그아웃 후 로그인 페이지로 이동
    navigate('/', { replace: true });
  };

  // 5. renderContent 함수 대신 Routes를 사용합니다.
  return (
    <ThemeProvider>
      <Suspense fallback={<LoadingScreen />}>
        <AuthProvider>
          <Routes>
            {/* 1. 로그인 페이지 */}
            <Route path="/" element={<AuthPage />} />

            {/* 2. OAuth 콜백 페이지 */}
            <Route path="/oauth/callback" element={<OAuthRedirectPage />} />

            {/* 3. 보호되는 라우트 (인증 필요) */}
            <Route element={<ProtectedRoute />}>
              {/* SelectWorkspacePage는 이제 props가 필요 없습니다. */}
              <Route path="/workspaces" element={<SelectWorkspacePage />} />

              {/* MainDashboard는 onLogout prop이 필요합니다. */}
              <Route
                path="/workspace/:workspaceId"
                element={<MainDashboard onLogout={handleLogout} />}
              />
            </Route>

            {/* 4. 일치하는 라우트가 없으면 로그인 페이지로 */}
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </AuthProvider>
      </Suspense>
    </ThemeProvider>
  );
};

export default App;
