// src/types/errors.ts

/**
 * 라우팅 오류 코드 타입
 */
export type RoutingErrorCode = 
  | 'INVALID_PREFIX'      // 잘못된 서비스 prefix 사용
  | 'MISSING_CONFIG'      // 환경 설정 누락
  | 'NETWORK_ERROR'       // 네트워크 연결 오류
  | 'ROUTING_MISMATCH'    // 라우팅 패턴 불일치
  | 'SERVICE_UNAVAILABLE' // 서비스 접근 불가
  | 'INVALID_URL_PATTERN' // 잘못된 URL 패턴
  | 'ENVIRONMENT_ERROR';  // 환경 설정 오류

/**
 * 라우팅 오류 인터페이스
 * 405, 404 오류에 대한 명확한 메시지 및 수정 제안을 제공합니다.
 */
export interface RoutingError {
  /** 오류 코드 */
  code: RoutingErrorCode;
  /** 사용자에게 표시할 오류 메시지 */
  message: string;
  /** 문제 해결을 위한 제안 */
  suggestion: string;
  /** 요청한 URL */
  requestedUrl: string;
  /** 예상되는 URL 패턴 */
  expectedPattern: string;
  /** 원본 HTTP 상태 코드 (선택적) */
  httpStatus?: number;
  /** 추가 디버깅 정보 (선택적) */
  debugInfo?: Record<string, any>;
}

/**
 * API 호출 로깅 정보 인터페이스
 */
export interface ApiCallLog {
  /** 서비스 이름 */
  serviceName: string;
  /** HTTP 메서드 */
  method: string;
  /** 완전한 요청 URL */
  fullUrl: string;
  /** 요청 시작 시간 */
  timestamp: string;
  /** 응답 상태 코드 (선택적) */
  statusCode?: number;
  /** 응답 시간 (ms, 선택적) */
  responseTime?: number;
  /** 오류 정보 (선택적) */
  error?: RoutingError;
}

/**
 * 환경별 라우팅 진단 정보
 */
export interface RoutingDiagnostics {
  /** 현재 배포 환경 */
  environment: string;
  /** 서비스별 base URL 매핑 */
  serviceUrls: Record<string, string>;
  /** 환경 변수 상태 */
  environmentVariables: Record<string, string | undefined>;
  /** 설정 검증 결과 */
  validationResults: Array<{
    check: string;
    passed: boolean;
    message: string;
  }>;
}