import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';

// 환경 변수 가져오기
const INJECTED_API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
const DEPLOYMENT_ENV = import.meta.env.VITE_DEPLOYMENT_ENV || 'k8s';

// ============================================================================
// 💡 환경별 API Base URL 설정
// ============================================================================
// VITE_DEPLOYMENT_ENV 값에 따라 API 호출 방식이 달라집니다:
// - 'docker-compose': 각 서비스별 포트로 직접 접근 (NGINX 프록시)
// - 'k8s': Ingress가 모든 라우팅 처리 (상대 경로 사용)
// - 'cloudfront': 별도 API 도메인 사용 (프로덕션)
// ============================================================================

const getApiBaseUrl = (path: string): string => {
  // docker-compose: 각 서비스별 포트 직접 접근 (로컬 개발용)
  if (DEPLOYMENT_ENV === 'docker-compose') {
    const baseUrl = INJECTED_API_BASE_URL || 'http://localhost';
    if (path?.includes('/api/auth')) return `${baseUrl}:8080/api/auth`;
    if (path?.includes('/api/users')) return `${baseUrl}:8090`;
    if (path?.includes('/api/workspaces')) return `${baseUrl}:8090`;
    if (path?.includes('/api/profiles')) return `${baseUrl}:8090`;
    if (path?.includes('/api/boards')) return `${baseUrl}:8000/api`;
    if (path?.includes('/api/chats')) return `${baseUrl}:8001${path}`;
    if (path?.includes('/api/notifications')) return `${baseUrl}:8002`;
    if (path?.includes('/api/storage')) return `${baseUrl}:8003/api`;
    return `${baseUrl}${path}`;
  }

  // k8s (Kind/EKS): Ingress가 모든 라우팅 처리
  // baseURL을 빈 문자열로 반환 → axios 호출 시 전체 경로(/api/workspaces/all) 그대로 사용
  if (DEPLOYMENT_ENV === 'k8s') {
    return '';
  }

  // cloudfront: 별도 API 도메인 사용 (프로덕션)
  // baseURL만 반환 → axios 호출 시 전체 경로(/api/workspaces/all) 추가됨
  if (DEPLOYMENT_ENV === 'cloudfront') {
    const apiDomain = import.meta.env.VITE_API_DOMAIN || 'https://api.wealist.co.kr';
    return apiDomain;
  }

  // fallback: 환경변수가 있으면 사용, 없으면 빈 문자열 (상대 경로)
  return INJECTED_API_BASE_URL || '';
};

export const AUTH_SERVICE_API_URL = getApiBaseUrl('/api/auth'); // auth-service (토큰 관리)
export const USER_REPO_API_URL = getApiBaseUrl('/api/users');
export const USER_SERVICE_API_URL = getApiBaseUrl('/api/users'); // 💡 user-service base URL (프로필 API용)
export const BOARD_SERVICE_API_URL = getApiBaseUrl('/api/boards/api');
export const CHAT_SERVICE_API_URL = getApiBaseUrl('/api/chats');
export const NOTI_SERVICE_API_URL = getApiBaseUrl('/api/notifications');
export const STORAGE_SERVICE_API_URL = getApiBaseUrl('/api/storage'); // storage-service (Google Drive like)

// ============================================================================
// Axios 인스턴스 생성
// ============================================================================

/**
 * Auth Service API (Java)를 위한 Axios 인스턴스 - 토큰 관리 전용
 */
export const authServiceClient = axios.create({
  baseURL: AUTH_SERVICE_API_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

/**
 * User Repo API (Java)를 위한 Axios 인스턴스
 */
export const userRepoClient = axios.create({
  baseURL: USER_REPO_API_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

/**
 * Board Service API (Go)를 위한 Axios 인스턴스
 */
export const boardServiceClient = axios.create({
  baseURL: BOARD_SERVICE_API_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

/**
 * Chat Service API (Go)를 위한 Axios 인스턴스
 */
export const chatServiceClient = axios.create({
  baseURL: CHAT_SERVICE_API_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

/**
 * Notification Service API (Go)를 위한 Axios 인스턴스
 */
export const notiServiceClient = axios.create({
  baseURL: NOTI_SERVICE_API_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

/**
 * Storage Service API (Go)를 위한 Axios 인스턴스 - Google Drive like storage
 */
export const storageServiceClient = axios.create({
  baseURL: STORAGE_SERVICE_API_URL,
  headers: { 'Content-Type': 'application/json' },
  withCredentials: true,
});

// ============================================================================
// 인증 갱신 헬퍼 함수 (기존 코드 유지)
// ============================================================================

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value?: unknown) => void;
  reject: (reason?: unknown) => void;
}> = [];

const processQueue = (error: Error | null, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

const performLogout = () => {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('nickName');
  localStorage.removeItem('userEmail');
  window.location.href = '/';
};

const refreshAccessToken = async (): Promise<string> => {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) {
    console.warn('⚠️ Refresh token not found. Logging out...');
    performLogout();
    throw new Error('No refresh token available');
  }

  try {
    // auth-service의 /api/auth/refresh 엔드포인트 호출
    const response = await axios.post(`${AUTH_SERVICE_API_URL}/refresh`, {
      refreshToken,
    });

    const { accessToken, refreshToken: newRefreshToken } = response.data;

    localStorage.setItem('accessToken', accessToken);
    if (newRefreshToken) {
      localStorage.setItem('refreshToken', newRefreshToken);
    }

    return accessToken;
  } catch (error) {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('nickName');
    localStorage.removeItem('userEmail');
    window.location.href = '/';
    throw error;
  }
};

// ============================================================================
// 인터셉터 설정
// ============================================================================

const setupRequestInterceptor = (client: AxiosInstance) => {
  client.interceptors.request.use(
    (config) => {
      const accessToken = localStorage.getItem('accessToken');
      if (accessToken && !config.headers.Authorization) {
        config.headers.Authorization = `Bearer ${accessToken}`;
      }
      return config;
    },
    (error) => {
      return Promise.reject(error);
    },
  );
};

const setupUnifiedResponseInterceptor = (client: AxiosInstance) => {
  client.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
      const originalRequest = error.config as InternalAxiosRequestConfig & {
        _retry?: boolean;
        retryCount?: number;
      };
      const status = error.response?.status;

      if (status === 401 && !originalRequest._retry) {
        if (isRefreshing) {
          return new Promise((resolve, reject) => {
            failedQueue.push({ resolve, reject });
          })
            .then((token) => {
              originalRequest.headers.Authorization = `Bearer ${token}`;
              return client(originalRequest);
            })
            .catch((err) => {
              return Promise.reject(err);
            });
        }

        originalRequest._retry = true;
        isRefreshing = true;

        try {
          const newAccessToken = await refreshAccessToken();
          processQueue(null, newAccessToken);
          originalRequest.headers.Authorization = `Bearer ${newAccessToken}`;
          return client(originalRequest);
        } catch (refreshError) {
          processQueue(refreshError as Error, null);
          return Promise.reject(refreshError);
        } finally {
          isRefreshing = false;
        }
      }

      if (status && status >= 400 && status < 599) {
        return Promise.reject(error);
      }

      if (!status && error.code !== 'ERR_CANCELED') {
        originalRequest.retryCount = originalRequest.retryCount || 0;

        if (originalRequest.retryCount >= 3) {
          console.error(`[Axios Interceptor] 최대 재시도 횟수 초과: ${originalRequest.url}`);
          return Promise.reject(error);
        }

        originalRequest.retryCount += 1;
        const delay = new Promise((resolve) => setTimeout(resolve, 1000));
        console.warn(
          `[Axios Interceptor] 재시도 중 (${originalRequest.retryCount}회): ${originalRequest.url}`,
        );
        await delay;
        return client(originalRequest);
      }
    },
  );
};

// 인터셉터 적용
setupRequestInterceptor(authServiceClient);
setupRequestInterceptor(userRepoClient);
setupRequestInterceptor(boardServiceClient);
setupRequestInterceptor(chatServiceClient);
setupRequestInterceptor(notiServiceClient);
setupRequestInterceptor(storageServiceClient);

setupUnifiedResponseInterceptor(authServiceClient);
setupUnifiedResponseInterceptor(userRepoClient);
setupUnifiedResponseInterceptor(boardServiceClient);
setupUnifiedResponseInterceptor(chatServiceClient);
setupUnifiedResponseInterceptor(notiServiceClient);
setupUnifiedResponseInterceptor(storageServiceClient);

export const getAuthHeaders = (token: string) => ({
  Authorization: `Bearer ${token}`,
  Accept: 'application/json',
});
