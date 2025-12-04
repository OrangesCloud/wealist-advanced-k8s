import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
// API 경로 설정 - auth-service 사용
import { AUTH_SERVICE_API_URL } from '../api/apiConfig';

interface AuthContextType {
  isAuthenticated: boolean;
  token: string | null;
  nickName: string | null;
  userEmail: string | null;
  userId: string | null; // ✅ 1. 타입 정의 추가
  logout: () => void;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

// App.tsx의 <Routes>와 BrowserRouter 사이에 위치해야 합니다.
export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const navigate = useNavigate();
  const [token, setToken] = useState<string | null>(null);
  const [reToken, setReToken] = useState<string | null>(null);
  const [nickName, setNickName] = useState<string | null>(null);
  const [userEmail, setUserEmail] = useState<string | null>(null);
  const [userId, setUserId] = useState<string | null>(null); // ✅ 2. State 추가
  const [isLoading, setIsLoading] = useState(true);

  // 1. 초기 로딩 시 localStorage에서 토큰 및 ID 로드
  useEffect(() => {
    const storedToken = localStorage.getItem('accessToken');
    const storedReToken = localStorage.getItem('refreshToken');
    const storedNickName = localStorage.getItem('nickName');
    const storedUserEmail = localStorage.getItem('userEmail');
    const storedUserId = localStorage.getItem('userId'); // ✅ 3. 로컬스토리지 읽기 추가
    console.log(storedUserId);
    if (storedToken && storedReToken && storedUserEmail) {
      setToken(storedToken);
      setNickName(storedNickName);
      setUserEmail(storedUserEmail);
      if (storedUserId) setUserId(storedUserId); // ✅ 4. State 복구
    }
    setIsLoading(false);
  }, []);

  // 3. 로그아웃 핸들러
  const logout = useCallback(async () => {
    // auth-service에 로그아웃 요청 (토큰 블랙리스트 추가)
    if (token) {
      try {
        await fetch(`${AUTH_SERVICE_API_URL}/logout`, {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${token}`,
          },
        });
      } catch (e) {
        console.error('Backend logout failed (proceeding with client-side cleanup)', e);
      }
    }

    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('nickName');
    localStorage.removeItem('userEmail');
    localStorage.removeItem('userId'); // ✅ 5. 로그아웃 시 삭제
    setToken(null);
    setReToken(null);
    setNickName(null);
    setUserEmail(null);
    setUserId(null); // ✅ 6. State 초기화
    // 로그아웃 후 로그인 페이지로 이동
    navigate('/', { replace: true });
  }, [token, navigate]);

  const value = {
    isAuthenticated: !!token,
    token,
    reToken,
    nickName,
    userEmail,
    userId, // ✅ 7. Context Value에 포함
    logout,
    isLoading,
  };

  // 💡 HACK: OAuthRedirectPage에서 setLoginState를 직접 호출해야 하므로,
  //     Provider 외부로 setLoginState를 노출하지 않고, localStorage 직접 접근을 권장합니다.
  //     (만약 App.tsx에 State가 있다면 prop drilling을 해야 함)
  //     최대한 간결하게 가기 위해, AuthContext에서는 상태만 제공하고 setLoginState는 주석 처리합니다.

  // 💡 대신, OAuthRedirectPage에서 setLoginState 대신 localStorage를 사용하고,
  //    App.tsx의 ProtectedRoute가 localStorage를 읽도록 하면 됩니다.

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
