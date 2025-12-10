import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import { logApiRequest, logApiResponse, logApiError } from '../utils/apiLogger';
import { createRoutingError, logRoutingError, formatUserFriendlyError } from '../utils/errorHandling';

// Axios 설정에 메타데이터 추가를 위한 타입 확장
declare module 'axios' {
  interface AxiosRequestConfig {
    metadata?: {
      startTime: number;
    };
  }
}

// ============================================================================
// 💡 환경 변수 및 타입 정의
// ============================================================================

type DeploymentEnvironment = 'docker-compose' | 'k8s' | 'cloudfront';
type ServiceName = 'auth' | 'users' | 'boards' | 'chat' | 'notifications' | 'storage';

interface EnvironmentConfig {
  deploymentEnv: DeploymentEnvironment;
  apiBaseUrl: string;
  apiDomain?: string;
}

interface ServiceEndpoints {
  auth: string;
  users: string;
  boards: string;
  chat: string;
  notifications: string;
  storage: string;
}

// 환경 변수 가져오기
const INJECTED_API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
const DEPLOYMENT_ENV = (import.meta.env.VITE_DEPLOYMENT_ENV || 'k8s') as DeploymentEnvironment;
const API_DOMAIN = import.meta.env.VITE_API_DOMAIN || 'https://api.wealist.co.kr';

// ============================================================================
// 💡 환경별 서비스 URL 매핑 테이블
// ============================================================================
// 각 배포 환경별로 서비스 URL을 명확하게 정의합니다.
// 이 매핑 테이블을 통해 일관된 패턴을 적용하고 유지보수성을 향상시킵니다.
// ============================================================================

/**
 * 환경별 서비스 URL 매핑 테이블
 * - docker-compose: 각 서비스별 포트로 직접 접근 (서비스 내부에서 /api 라우팅 처리)
 * - k8s: Ingress가 모든 라우팅 처리 (Ingress에서 /api/{service} → 서비스 내부 /api)
 * - cloudfront: 별도 API 도메인 사용 (프로덕션)
 */
const ENVIRONMENT_CONFIGS: Record<DeploymentEnvironment, ServiceEndpoints> = {
  'docker-compose': {
    auth: `${INJECTED_API_BASE_URL || 'http://localhost'}:8080`,
    users: `${INJECTED_API_BASE_URL || 'http://localhost'}:8090`,
    boards: `${INJECTED_API_BASE_URL || 'http://localhost'}:8000`,
    chat: `${INJECTED_API_BASE_URL || 'http://localhost'}:8001`,
    notifications: `${INJECTED_API_BASE_URL || 'http://localhost'}:8002`,
    storage: `${INJECTED_API_BASE_URL || 'http://localhost'}:8003`,
  },
  'k8s': {
    // K8s 환경에서는 Ingress가 라우팅을 처리하므로 빈 문자열 사용
    auth: '',
    users: '',
    boards: '',
    chat: '',
    notifications: '',
    storage: '',
  },
  'cloudfront': {
    // CloudFront 환경에서는 모든 서비스가 동일한 API 도메인 사용
    auth: API_DOMAIN,
    users: API_DOMAIN,
    boards: API_DOMAIN,
    chat: API_DOMAIN,
    notifications: API_DOMAIN,
    storage: API_DOMAIN,
  },
};

/**
 * 서비스별 API 경로 prefix 매핑
 * - K8s: Ingress 라우팅 규칙과 일치 (/api/{service})
 * - Docker Compose: 서비스 내부 라우팅 (/api)
 * - CloudFront: Ingress와 동일 (/api/{service})
 */
const SERVICE_PATHS: Record<ServiceName, string> = {
  auth: '/api/auth',
  users: '/api/users',
  boards: '/api/boards',
  chat: '/api/chat',
  notifications: '/api/notifications',
  storage: '/api/storage',
};

// ============================================================================
// 💡 환경 감지 및 설정 검증 로직
// ============================================================================

/**
 * 현재 배포 환경 설정을 가져옵니다
 */
const getCurrentEnvironmentConfig = (): EnvironmentConfig => {
  return {
    deploymentEnv: DEPLOYMENT_ENV,
    apiBaseUrl: INJECTED_API_BASE_URL,
    apiDomain: API_DOMAIN,
  };
};

/**
 * 환경 설정 유효성을 검증합니다
 */
const validateEnvironmentConfig = (config: EnvironmentConfig): boolean => {
  // 필수 환경 변수 확인
  if (!config.deploymentEnv) {
    console.warn('⚠️ VITE_DEPLOYMENT_ENV is not set, using default: k8s');
    return false;
  }

  // 지원되는 환경인지 확인
  if (!['docker-compose', 'k8s', 'cloudfront'].includes(config.deploymentEnv)) {
    console.error(`❌ Unsupported deployment environment: ${config.deploymentEnv}`);
    return false;
  }

  // CloudFront 환경에서 API 도메인 확인
  if (config.deploymentEnv === 'cloudfront' && !config.apiDomain) {
    console.warn('⚠️ VITE_API_DOMAIN is not set for cloudfront environment');
    return false;
  }

  return true;
};

/**
 * 안전한 기본값을 적용합니다
 */
const applySafeDefaults = (config: EnvironmentConfig): EnvironmentConfig => {
  const safeConfig = { ...config };

  // 잘못된 환경 설정 시 k8s로 기본값 설정
  if (!validateEnvironmentConfig(config)) {
    console.warn('⚠️ Invalid environment config detected, falling back to k8s defaults');
    safeConfig.deploymentEnv = 'k8s';
  }

  return safeConfig;
};

/**
 * 환경별 설정 상태를 진단합니다
 */
const diagnoseEnvironmentConfig = (): void => {
  const config = getCurrentEnvironmentConfig();
  
  console.group('🔍 API Configuration Diagnosis');
  console.log('Environment:', config.deploymentEnv);
  console.log('API Base URL:', config.apiBaseUrl || '(not set)');
  console.log('API Domain:', config.apiDomain || '(not set)');
  
  // 환경별 특정 검증
  switch (config.deploymentEnv) {
    case 'docker-compose':
      if (!config.apiBaseUrl) {
        console.warn('⚠️ VITE_API_BASE_URL not set for docker-compose environment');
        console.log('💡 Suggestion: Set VITE_API_BASE_URL=http://localhost in your .env file');
      }
      break;
      
    case 'k8s':
      if (config.apiBaseUrl) {
        console.info('ℹ️ VITE_API_BASE_URL is set but will be ignored in k8s environment');
      }
      console.log('✅ K8s environment uses Ingress routing with relative paths');
      break;
      
    case 'cloudfront':
      if (!config.apiDomain) {
        console.error('❌ VITE_API_DOMAIN is required for cloudfront environment');
        console.log('💡 Suggestion: Set VITE_API_DOMAIN=https://api.yourdomain.com');
      }
      break;
      
    default:
      console.error(`❌ Unknown deployment environment: ${config.deploymentEnv}`);
      console.log('💡 Supported environments: docker-compose, k8s, cloudfront');
  }
  
  console.groupEnd();
};

/**
 * 런타임에서 설정 검증을 수행합니다
 */
const performRuntimeValidation = (): boolean => {
  const config = getCurrentEnvironmentConfig();
  let isValid = true;
  
  // 필수 환경 변수 검증
  const requiredVars: Array<{ key: string; value: string | undefined; required: boolean }> = [
    { key: 'VITE_DEPLOYMENT_ENV', value: config.deploymentEnv, required: true },
  ];
  
  // 환경별 추가 검증
  if (config.deploymentEnv === 'cloudfront') {
    requiredVars.push({ key: 'VITE_API_DOMAIN', value: config.apiDomain, required: true });
  }
  
  requiredVars.forEach(({ key, value, required }) => {
    if (required && !value) {
      console.error(`❌ Required environment variable ${key} is not set`);
      isValid = false;
    }
  });
  
  return isValid;
};

/**
 * 특정 서비스의 base URL을 생성합니다
 */
const getServiceBaseUrl = (serviceName: ServiceName): string => {
  const config = applySafeDefaults(getCurrentEnvironmentConfig());
  const serviceEndpoints = ENVIRONMENT_CONFIGS[config.deploymentEnv];
  
  if (!serviceEndpoints) {
    console.error(`❌ No configuration found for environment: ${config.deploymentEnv}`);
    return '';
  }

  const baseUrl = serviceEndpoints[serviceName];
  const servicePath = SERVICE_PATHS[serviceName];
  
  // 환경별 URL 생성 로직
  let fullUrl = '';
  
  if (config.deploymentEnv === 'k8s') {
    // K8s 환경: Ingress가 /api/{service}/* → 서비스 내부 /api/* 라우팅
    fullUrl = servicePath;
  } else if (config.deploymentEnv === 'docker-compose') {
    // Docker Compose 환경: 직접 서비스 포트 접근, 서비스에서 /api 제거된 경로 사용
    fullUrl = baseUrl;
  } else {
    // CloudFront 환경: API Gateway에서 /api/{service}/* 라우팅
    fullUrl = baseUrl + servicePath;
  }
  
  // 개발 환경에서 디버깅을 위한 로깅
  if (import.meta.env.DEV) {
    console.log(`🔧 [${serviceName}] Environment: ${config.deploymentEnv}, Full URL: ${fullUrl}`);
  }

  return fullUrl;
};

// ============================================================================
// 💡 초기화 및 진단
// ============================================================================

/**
 * API 클라이언트 초기화 시 base URL 검증
 */
const validateClientBaseUrl = (serviceName: ServiceName, baseUrl: string): {
  isValid: boolean;
  issues: string[];
  suggestions: string[];
} => {
  const issues: string[] = [];
  const suggestions: string[] = [];
  const config = getCurrentEnvironmentConfig();
  
  // 기본 URL 유효성 검사
  if (!baseUrl) {
    issues.push('Base URL is empty or undefined');
    suggestions.push(`Set proper environment variables for ${serviceName} service`);
  }
  
  // URL에 undefined가 포함되어 있는지 확인
  if (baseUrl && baseUrl.includes('undefined')) {
    issues.push('Base URL contains undefined values');
    suggestions.push('Check that all required environment variables are properly set');
  }
  
  // 환경별 특정 검증
  switch (config.deploymentEnv) {
    case 'docker-compose':
      if (baseUrl && !baseUrl.includes('localhost') && !baseUrl.includes('127.0.0.1')) {
        issues.push('Docker Compose environment should use localhost URLs');
        suggestions.push('Set VITE_API_BASE_URL=http://localhost in your environment');
      }
      break;
      
    case 'k8s':
      if (baseUrl && baseUrl.includes('localhost')) {
        issues.push('K8s environment should not use localhost URLs');
        suggestions.push('Use relative paths for K8s Ingress routing');
      }
      if (baseUrl && !baseUrl.startsWith('/api/')) {
        issues.push('K8s environment should use /api/{service} paths');
        suggestions.push(`Expected path format: /api/${serviceName}`);
      }
      break;
      
    case 'cloudfront':
      if (baseUrl && !baseUrl.startsWith('https://')) {
        issues.push('CloudFront environment should use HTTPS URLs');
        suggestions.push('Set VITE_API_DOMAIN=https://api.yourdomain.com');
      }
      break;
  }
  
  return {
    isValid: issues.length === 0,
    issues,
    suggestions,
  };
};

/**
 * 모든 API 클라이언트의 base URL을 검증합니다
 */
const validateAllClientConfigurations = (): boolean => {
  const services: ServiceName[] = ['auth', 'users', 'boards', 'chat', 'notifications', 'storage'];
  let allValid = true;
  
  console.group('🔍 API Client Configuration Validation');
  
  services.forEach(serviceName => {
    const baseUrl = getServiceBaseUrl(serviceName);
    const validation = validateClientBaseUrl(serviceName, baseUrl);
    
    const icon = validation.isValid ? '✅' : '❌';
    console.log(`${icon} ${serviceName}: ${baseUrl || '(empty)'}`);
    
    if (!validation.isValid) {
      allValid = false;
      validation.issues.forEach(issue => {
        console.warn(`  ⚠️ Issue: ${issue}`);
      });
      validation.suggestions.forEach(suggestion => {
        console.info(`  💡 Suggestion: ${suggestion}`);
      });
    }
  });
  
  if (!allValid) {
    console.error('❌ Some API client configurations are invalid');
    console.group('🔧 Troubleshooting Steps');
    console.log('1. Check your environment variables (VITE_DEPLOYMENT_ENV, VITE_API_BASE_URL)');
    console.log('2. Ensure the deployment environment matches your actual setup');
    console.log('3. Verify that all required services are running and accessible');
    console.log('4. Check network connectivity and firewall settings');
    console.groupEnd();
  } else {
    console.log('✅ All API client configurations are valid');
  }
  
  console.groupEnd();
  return allValid;
};

/**
 * API 설정 시스템을 초기화합니다
 */
const initializeApiConfig = (): void => {
  // 개발 환경에서만 진단 실행
  if (import.meta.env.DEV) {
    diagnoseEnvironmentConfig();
    
    // API 클라이언트 설정 검증
    setTimeout(() => {
      validateAllClientConfigurations();
      diagnoseApiConfiguration();
    }, 100); // 약간의 지연을 두어 다른 로그와 구분
  }
  
  // 런타임 검증 수행
  const isValid = performRuntimeValidation();
  
  if (!isValid) {
    console.error('❌ API configuration validation failed');
    if (import.meta.env.DEV) {
      console.log('💡 Check your environment variables and try again');
    }
  }
};

// 모듈 로드 시 초기화 실행
initializeApiConfig();

// ============================================================================
// 💡 유틸리티 함수 내보내기
// ============================================================================

/**
 * 현재 환경 설정 정보를 반환합니다 (디버깅용)
 */
export const getEnvironmentInfo = (): EnvironmentConfig => {
  return getCurrentEnvironmentConfig();
};

/**
 * 설정 진단을 수동으로 실행합니다 (디버깅용)
 */
export const runConfigDiagnosis = (): void => {
  diagnoseEnvironmentConfig();
};

/**
 * API 클라이언트 설정 검증을 수동으로 실행합니다 (디버깅용)
 */
export const validateClientConfigurations = (): boolean => {
  return validateAllClientConfigurations();
};

/**
 * 특정 서비스의 URL 패턴을 검증합니다 (디버깅용)
 */
export const validateServiceUrlPattern = (
  serviceName: ServiceName,
  path: string = ''
): {
  isValid: boolean;
  expectedPattern: string;
  actualUrl: string;
  issues: string[];
} => {
  const baseUrl = getServiceBaseUrl(serviceName);
  const fullUrl = getFullApiUrl(serviceName, path);
  
  return validateRequestUrlPattern(serviceName, fullUrl, baseUrl);
};

/**
 * 요청 URL이 예상 패턴과 일치하는지 검증합니다
 */
const validateRequestUrlPattern = (
  serviceName: ServiceName,
  requestUrl: string,
  baseUrl: string
): {
  isValid: boolean;
  expectedPattern: string;
  actualUrl: string;
  issues: string[];
} => {
  const config = getCurrentEnvironmentConfig();
  const issues: string[] = [];
  let expectedPattern = '';
  
  // 환경별 예상 패턴 정의
  switch (config.deploymentEnv) {
    case 'docker-compose':
      expectedPattern = `${baseUrl || 'http://localhost:{port}'}/*`;
      if (!requestUrl.includes('localhost')) {
        issues.push('Docker Compose URLs should use localhost');
      }
      break;
      
    case 'k8s':
      expectedPattern = `/api/${serviceName}/*`;
      if (!requestUrl.startsWith(`/api/${serviceName}`)) {
        issues.push(`K8s URLs should start with /api/${serviceName}`);
      }
      break;
      
    case 'cloudfront':
      expectedPattern = `${baseUrl || 'https://api.domain.com'}/api/${serviceName}/*`;
      if (!requestUrl.includes(`/api/${serviceName}`)) {
        issues.push(`CloudFront URLs should include /api/${serviceName} path`);
      }
      break;
  }
  
  // 중복된 /api 패턴 검사
  const apiCount = (requestUrl.match(/\/api/g) || []).length;
  if (apiCount > 1) {
    issues.push('Duplicate /api prefix detected in URL');
  }
  
  return {
    isValid: issues.length === 0,
    expectedPattern,
    actualUrl: requestUrl,
    issues,
  };
};

/**
 * 서비스별 완전한 API URL을 생성하고 검증합니다 (디버깅용)
 */
export const getFullApiUrl = (serviceName: ServiceName, path: string = ''): string => {
  const baseUrl = getServiceBaseUrl(serviceName);
  const servicePath = SERVICE_PATHS[serviceName];
  
  let fullUrl = '';
  
  if (!baseUrl) {
    // K8s 환경에서는 상대 경로 사용
    fullUrl = `${servicePath}${path}`;
  } else {
    fullUrl = `${baseUrl}${path}`;
  }
  
  // 개발 환경에서 URL 패턴 검증
  if (import.meta.env.DEV) {
    const validation = validateRequestUrlPattern(serviceName, fullUrl, baseUrl);
    
    if (!validation.isValid) {
      console.warn(`⚠️ URL Pattern Validation Failed for ${serviceName}:`);
      console.warn(`  Expected: ${validation.expectedPattern}`);
      console.warn(`  Actual: ${validation.actualUrl}`);
      validation.issues.forEach(issue => {
        console.warn(`  Issue: ${issue}`);
      });
    }
  }
  
  return fullUrl;
};

/**
 * 모든 서비스의 설정 상태를 검증합니다 (디버깅용)
 */
export const validateAllServiceConfigs = (): Record<ServiceName, {
  baseUrl: string;
  isValid: boolean;
  issues: string[];
}> => {
  const services: ServiceName[] = ['auth', 'users', 'boards', 'chat', 'notifications', 'storage'];
  const results: Record<string, any> = {};
  
  services.forEach(serviceName => {
    const baseUrl = getServiceBaseUrl(serviceName);
    const issues: string[] = [];
    
    // URL 유효성 검사
    if (!baseUrl) {
      issues.push('Base URL is empty');
    } else if (baseUrl.includes('undefined')) {
      issues.push('Base URL contains undefined values');
    }
    
    // 환경별 특정 검증
    const config = getCurrentEnvironmentConfig();
    if (config.deploymentEnv === 'docker-compose' && !baseUrl.includes(':')) {
      issues.push('Docker Compose environment should include port number');
    }
    
    if (config.deploymentEnv === 'k8s' && baseUrl.includes('localhost')) {
      issues.push('K8s environment should not use localhost');
    }
    
    results[serviceName] = {
      baseUrl,
      isValid: issues.length === 0,
      issues,
    };
  });
  
  return results as Record<ServiceName, { baseUrl: string; isValid: boolean; issues: string[] }>;
};

/**
 * API 설정 문제 진단 및 해결 제안 (디버깅용)
 */
export const diagnoseApiConfiguration = (): void => {
  if (!import.meta.env.DEV) return;
  
  console.group('🔧 API Configuration Diagnosis');
  
  // 환경 설정 진단
  const config = getCurrentEnvironmentConfig();
  console.log('Current Environment:', config);
  
  // 서비스별 설정 검증
  const validationResults = validateAllServiceConfigs();
  console.log('Service Configuration Validation:');
  
  Object.entries(validationResults).forEach(([serviceName, result]) => {
    const icon = result.isValid ? '✅' : '❌';
    console.log(`${icon} ${serviceName}: ${result.baseUrl}`);
    
    if (result.issues.length > 0) {
      result.issues.forEach(issue => {
        console.warn(`  ⚠️ ${issue}`);
      });
    }
  });
  
  // 일반적인 문제 해결 제안
  const hasIssues = Object.values(validationResults).some(result => !result.isValid);
  if (hasIssues) {
    console.group('💡 Troubleshooting Suggestions');
    
    if (config.deploymentEnv === 'docker-compose' && !config.apiBaseUrl) {
      console.log('• Set VITE_API_BASE_URL=http://localhost in your .env file');
    }
    
    if (config.deploymentEnv === 'cloudfront' && !config.apiDomain) {
      console.log('• Set VITE_API_DOMAIN=https://api.yourdomain.com in your .env file');
    }
    
    console.log('• Check that VITE_DEPLOYMENT_ENV matches your actual deployment environment');
    console.log('• Verify that all required environment variables are set');
    console.log('• Ensure Ingress routing rules match the expected /api/{service}/* pattern');
    
    console.groupEnd();
  }
  
  console.groupEnd();
};

// ============================================================================
// 💡 서비스별 API 클라이언트 URL 생성
// ============================================================================

export const AUTH_SERVICE_API_URL = getServiceBaseUrl('auth');
export const USER_REPO_API_URL = getServiceBaseUrl('users');
export const USER_SERVICE_API_URL = getServiceBaseUrl('users');
export const BOARD_SERVICE_API_URL = getServiceBaseUrl('boards');
export const CHAT_SERVICE_API_URL = getServiceBaseUrl('chat');
export const NOTI_SERVICE_API_URL = getServiceBaseUrl('notifications');
export const STORAGE_SERVICE_API_URL = getServiceBaseUrl('storage');

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
// 헬퍼 함수
// ============================================================================

/**
 * Axios 설정에서 서비스 이름 추출
 */
const extractServiceNameFromConfig = (config: InternalAxiosRequestConfig): string => {
  const baseURL = config.baseURL || '';
  const url = config.url || '';
  
  // base URL에서 서비스 이름 추출 시도
  const baseUrlMatch = baseURL.match(/\/api\/(\w+)$/);
  if (baseUrlMatch) {
    return baseUrlMatch[1];
  }
  
  // 전체 URL에서 서비스 이름 추출 시도
  const fullUrlMatch = `${baseURL}${url}`.match(/\/api\/(\w+)/);
  if (fullUrlMatch) {
    return fullUrlMatch[1];
  }
  
  // 포트 번호로 서비스 추정 (docker-compose 환경)
  const portMatch = baseURL.match(/:(\d+)/);
  if (portMatch) {
    const port = portMatch[1];
    const portToService: Record<string, string> = {
      '8080': 'auth',
      '8090': 'users',
      '8000': 'boards', 
      '8001': 'chat',
      '8002': 'notifications',
      '8003': 'storage',
    };
    return portToService[port] || 'unknown';
  }
  
  return 'unknown';
};

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
      // 인증 토큰 추가
      const accessToken = localStorage.getItem('accessToken');
      if (accessToken && !config.headers.Authorization) {
        config.headers.Authorization = `Bearer ${accessToken}`;
      }
      
      // 요청 시작 시간 기록 (응답 시간 측정용)
      config.metadata = { startTime: Date.now() };
      
      // API 요청 로깅
      logApiRequest(config);
      
      return config;
    },
    (error) => {
      console.error('Request interceptor error:', error);
      return Promise.reject(error);
    },
  );
};

const setupUnifiedResponseInterceptor = (client: AxiosInstance) => {
  client.interceptors.response.use(
    (response) => {
      // 성공 응답 로깅
      const startTime = response.config.metadata?.startTime || Date.now();
      logApiResponse(response, startTime);
      
      return response;
    },
    async (error: AxiosError) => {
      const originalRequest = error.config as InternalAxiosRequestConfig & {
        _retry?: boolean;
        retryCount?: number;
        metadata?: { startTime: number };
      };
      const status = error.response?.status;
      const startTime = originalRequest?.metadata?.startTime || Date.now();

      // API 오류 로깅
      if (originalRequest) {
        logApiError(error, startTime);
      }

      // 401 Unauthorized 처리 (토큰 갱신)
      if (status === 401 && originalRequest && !originalRequest._retry) {
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

      // 라우팅 관련 오류 처리 (405, 404 등)
      if (status === 405 || status === 404) {
        if (originalRequest && import.meta.env.DEV) {
          const serviceName = extractServiceNameFromConfig(originalRequest);
          const routingError = createRoutingError(error, originalRequest.url || '', serviceName);
          logRoutingError(routingError);
          
          // 사용자 친화적 오류 메시지로 변환
          const userFriendlyMessage = formatUserFriendlyError(routingError);
          console.warn('User-friendly error message:', userFriendlyMessage);
        }
      }

      // 4xx, 5xx 오류는 그대로 전달
      if (status && status >= 400 && status < 599) {
        return Promise.reject(error);
      }

      // 네트워크 오류 재시도 로직
      if (!status && error.code !== 'ERR_CANCELED' && originalRequest) {
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

      return Promise.reject(error);
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
