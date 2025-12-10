// src/utils/apiLogger.ts

import type { AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';
import type { ApiCallLog } from '../types/errors';
import { createRoutingError, logRoutingError } from './errorHandling';

/**
 * API 호출 로그 저장소 (개발 환경용)
 */
class ApiLogStore {
  private logs: ApiCallLog[] = [];
  private maxLogs = 100; // 최대 로그 개수

  /**
   * 로그 추가
   */
  addLog(log: ApiCallLog): void {
    this.logs.unshift(log); // 최신 로그를 앞에 추가
    
    // 최대 개수 초과 시 오래된 로그 제거
    if (this.logs.length > this.maxLogs) {
      this.logs = this.logs.slice(0, this.maxLogs);
    }
  }

  /**
   * 모든 로그 조회
   */
  getAllLogs(): ApiCallLog[] {
    return [...this.logs];
  }

  /**
   * 특정 서비스의 로그만 조회
   */
  getLogsByService(serviceName: string): ApiCallLog[] {
    return this.logs.filter(log => log.serviceName === serviceName);
  }

  /**
   * 오류가 발생한 로그만 조회
   */
  getErrorLogs(): ApiCallLog[] {
    return this.logs.filter(log => log.error);
  }

  /**
   * 로그 초기화
   */
  clearLogs(): void {
    this.logs = [];
  }

  /**
   * 로그 통계 조회
   */
  getLogStats(): {
    total: number;
    errors: number;
    byService: Record<string, number>;
    byStatus: Record<string, number>;
  } {
    const stats = {
      total: this.logs.length,
      errors: 0,
      byService: {} as Record<string, number>,
      byStatus: {} as Record<string, number>,
    };

    this.logs.forEach(log => {
      // 서비스별 통계
      stats.byService[log.serviceName] = (stats.byService[log.serviceName] || 0) + 1;
      
      // 상태 코드별 통계
      if (log.statusCode) {
        const statusGroup = `${Math.floor(log.statusCode / 100)}xx`;
        stats.byStatus[statusGroup] = (stats.byStatus[statusGroup] || 0) + 1;
      }
      
      // 오류 통계
      if (log.error) {
        stats.errors++;
      }
    });

    return stats;
  }
}

// 전역 로그 저장소 인스턴스
const apiLogStore = new ApiLogStore();

/**
 * 서비스 이름을 base URL에서 추출
 */
const extractServiceName = (baseURL: string = '', url: string = ''): string => {
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

/**
 * 완전한 요청 URL 생성
 */
const buildFullUrl = (config: AxiosRequestConfig): string => {
  const baseURL = config.baseURL || '';
  const url = config.url || '';
  
  // 절대 URL인 경우 그대로 반환
  if (url.startsWith('http')) {
    return url;
  }
  
  // 상대 URL인 경우 base URL과 결합
  const fullUrl = baseURL + url;
  
  // 쿼리 파라미터 추가
  if (config.params) {
    const searchParams = new URLSearchParams();
    Object.entries(config.params).forEach(([key, value]) => {
      if (value !== undefined && value !== null) {
        searchParams.append(key, String(value));
      }
    });
    
    const queryString = searchParams.toString();
    if (queryString) {
      return `${fullUrl}${fullUrl.includes('?') ? '&' : '?'}${queryString}`;
    }
  }
  
  return fullUrl;
};

/**
 * API 요청 시작 로깅
 */
export const logApiRequest = (config: AxiosRequestConfig): void => {
  if (!import.meta.env.DEV) return;
  
  const serviceName = extractServiceName(config.baseURL, config.url);
  const fullUrl = buildFullUrl(config);
  const method = (config.method || 'GET').toUpperCase();
  
  const log: ApiCallLog = {
    serviceName,
    method,
    fullUrl,
    timestamp: new Date().toISOString(),
  };
  
  // 콘솔에 요청 로그 출력
  console.log(
    `🚀 [${serviceName.toUpperCase()}] ${method} ${fullUrl}`,
    {
      timestamp: log.timestamp,
      headers: config.headers,
      data: config.data,
    }
  );
  
  // 로그 저장소에 추가
  apiLogStore.addLog(log);
};

/**
 * API 응답 성공 로깅
 */
export const logApiResponse = (
  response: AxiosResponse,
  startTime: number = Date.now()
): void => {
  if (!import.meta.env.DEV) return;
  
  const config = response.config;
  const serviceName = extractServiceName(config.baseURL, config.url);
  const fullUrl = buildFullUrl(config);
  const method = (config.method || 'GET').toUpperCase();
  const responseTime = Date.now() - startTime;
  
  const log: ApiCallLog = {
    serviceName,
    method,
    fullUrl,
    timestamp: new Date().toISOString(),
    statusCode: response.status,
    responseTime,
  };
  
  // 콘솔에 응답 로그 출력
  const statusIcon = response.status < 400 ? '✅' : '⚠️';
  console.log(
    `${statusIcon} [${serviceName.toUpperCase()}] ${response.status} ${method} ${fullUrl} (${responseTime}ms)`,
    {
      data: response.data,
      headers: response.headers,
    }
  );
  
  // 로그 저장소에 기존 로그 업데이트 또는 새 로그 추가
  const existingLogIndex = apiLogStore.getAllLogs().findIndex(
    existingLog => 
      existingLog.serviceName === serviceName &&
      existingLog.method === method &&
      existingLog.fullUrl === fullUrl &&
      !existingLog.statusCode // 아직 응답이 기록되지 않은 로그
  );
  
  if (existingLogIndex >= 0) {
    // 기존 로그 업데이트
    const logs = apiLogStore.getAllLogs();
    logs[existingLogIndex] = { ...logs[existingLogIndex], ...log };
  } else {
    // 새 로그 추가
    apiLogStore.addLog(log);
  }
};

/**
 * API 오류 로깅
 */
export const logApiError = (
  error: AxiosError,
  startTime: number = Date.now()
): void => {
  if (!import.meta.env.DEV) return;
  
  const config = error.config;
  if (!config) return;
  
  const serviceName = extractServiceName(config.baseURL, config.url);
  const fullUrl = buildFullUrl(config);
  const method = (config.method || 'GET').toUpperCase();
  const responseTime = Date.now() - startTime;
  
  // 라우팅 오류 생성
  const routingError = createRoutingError(error, fullUrl, serviceName);
  
  const log: ApiCallLog = {
    serviceName,
    method,
    fullUrl,
    timestamp: new Date().toISOString(),
    statusCode: error.response?.status,
    responseTime,
    error: routingError,
  };
  
  // 콘솔에 오류 로그 출력
  console.error(
    `❌ [${serviceName.toUpperCase()}] ${error.response?.status || 'NETWORK'} ${method} ${fullUrl} (${responseTime}ms)`,
    {
      error: error.message,
      response: error.response?.data,
      config: {
        baseURL: config.baseURL,
        url: config.url,
        method: config.method,
        headers: config.headers,
      },
    }
  );
  
  // 라우팅 오류 상세 로깅
  logRoutingError(routingError);
  
  // 로그 저장소에 기존 로그 업데이트 또는 새 로그 추가
  const existingLogIndex = apiLogStore.getAllLogs().findIndex(
    existingLog => 
      existingLog.serviceName === serviceName &&
      existingLog.method === method &&
      existingLog.fullUrl === fullUrl &&
      !existingLog.statusCode // 아직 응답이 기록되지 않은 로그
  );
  
  if (existingLogIndex >= 0) {
    // 기존 로그 업데이트
    const logs = apiLogStore.getAllLogs();
    logs[existingLogIndex] = { ...logs[existingLogIndex], ...log };
  } else {
    // 새 로그 추가
    apiLogStore.addLog(log);
  }
};

/**
 * 개발자 도구용 API 로그 조회 함수들
 * 브라우저 콘솔에서 사용 가능
 */
if (import.meta.env.DEV && typeof window !== 'undefined') {
  // 전역 객체에 디버깅 함수 추가
  (window as any).apiLogs = {
    /**
     * 모든 API 로그 조회
     */
    getAll: () => apiLogStore.getAllLogs(),
    
    /**
     * 특정 서비스의 로그 조회
     */
    getByService: (serviceName: string) => apiLogStore.getLogsByService(serviceName),
    
    /**
     * 오류 로그만 조회
     */
    getErrors: () => apiLogStore.getErrorLogs(),
    
    /**
     * 로그 통계 조회
     */
    getStats: () => apiLogStore.getLogStats(),
    
    /**
     * 로그 초기화
     */
    clear: () => apiLogStore.clearLogs(),
    
    /**
     * 최근 N개 로그 조회
     */
    getRecent: (count: number = 10) => apiLogStore.getAllLogs().slice(0, count),
    
    /**
     * 특정 상태 코드의 로그 조회
     */
    getByStatus: (statusCode: number) => 
      apiLogStore.getAllLogs().filter(log => log.statusCode === statusCode),
  };
  
  if (typeof console !== 'undefined') {
    console.log('🔧 API Logging enabled. Use window.apiLogs to access debugging functions.');
    console.log('Available functions: getAll(), getByService(name), getErrors(), getStats(), clear(), getRecent(count), getByStatus(code)');
  }
}

/**
 * 로그 저장소 인스턴스 내보내기 (테스트용)
 */
export { apiLogStore };