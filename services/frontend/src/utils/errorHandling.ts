// src/utils/errorHandling.ts

import { AxiosError } from 'axios';
import type { RoutingError, RoutingErrorCode, RoutingDiagnostics } from '../types/errors';

/**
 * HTTP 상태 코드에 따른 라우팅 오류 생성
 */
export const createRoutingError = (
  error: AxiosError,
  requestedUrl: string,
  serviceName: string,
): RoutingError => {
  const status = error.response?.status;
  const config = error.config;
  
  // 기본 오류 정보
  let code: RoutingErrorCode = 'NETWORK_ERROR';
  let message = '알 수 없는 오류가 발생했습니다.';
  let suggestion = '네트워크 연결을 확인하고 다시 시도해주세요.';
  let expectedPattern = '';

  // HTTP 상태 코드별 처리
  switch (status) {
    case 405: // Method Not Allowed
      code = 'INVALID_PREFIX';
      message = `${serviceName} 서비스 API 호출이 잘못된 경로로 라우팅되었습니다.`;
      suggestion = `Ingress 라우팅 규칙을 확인하고, API 호출이 올바른 /api/${serviceName} prefix를 사용하는지 확인해주세요.`;
      expectedPattern = `/api/${serviceName}/*`;
      break;
      
    case 404: // Not Found
      code = 'ROUTING_MISMATCH';
      message = `요청한 엔드포인트를 찾을 수 없습니다.`;
      suggestion = `URL 패턴이 올바른지 확인하고, 서비스가 정상적으로 실행 중인지 확인해주세요.`;
      expectedPattern = `/api/${serviceName}/*`;
      break;
      
    case 502: // Bad Gateway
    case 503: // Service Unavailable
    case 504: // Gateway Timeout
      code = 'SERVICE_UNAVAILABLE';
      message = `${serviceName} 서비스에 연결할 수 없습니다.`;
      suggestion = '서비스가 정상적으로 실행 중인지 확인하고, 잠시 후 다시 시도해주세요.';
      expectedPattern = `서비스 상태 확인 필요`;
      break;
      
    default:
      if (!status) {
        code = 'NETWORK_ERROR';
        message = '네트워크 연결 오류가 발생했습니다.';
        suggestion = '인터넷 연결을 확인하고 다시 시도해주세요.';
      } else {
        code = 'NETWORK_ERROR';
        message = `HTTP ${status} 오류가 발생했습니다.`;
        suggestion = '서버 상태를 확인하고 다시 시도해주세요.';
      }
      expectedPattern = '정상적인 HTTP 응답';
  }

  return {
    code,
    message,
    suggestion,
    requestedUrl,
    expectedPattern,
    httpStatus: status,
    debugInfo: {
      serviceName,
      method: config?.method?.toUpperCase(),
      baseURL: config?.baseURL,
      url: config?.url,
      headers: config?.headers,
      errorMessage: error.message,
      errorCode: error.code,
    },
  };
};

/**
 * 환경 설정 오류 생성
 */
export const createConfigurationError = (
  missingVariable: string,
  currentEnvironment: string,
): RoutingError => {
  return {
    code: 'MISSING_CONFIG',
    message: `필수 환경 변수 ${missingVariable}가 설정되지 않았습니다.`,
    suggestion: `${currentEnvironment} 환경에 맞는 ${missingVariable} 값을 설정해주세요.`,
    requestedUrl: '환경 설정',
    expectedPattern: `${missingVariable}=<적절한 값>`,
    debugInfo: {
      environment: currentEnvironment,
      missingVariable,
    },
  };
};

/**
 * URL 패턴 검증 오류 생성
 */
export const createUrlPatternError = (
  actualUrl: string,
  expectedPattern: string,
  serviceName: string,
): RoutingError => {
  return {
    code: 'INVALID_URL_PATTERN',
    message: `생성된 URL이 예상 패턴과 일치하지 않습니다.`,
    suggestion: `${serviceName} 서비스의 API 설정을 확인하고, 올바른 base URL이 사용되는지 확인해주세요.`,
    requestedUrl: actualUrl,
    expectedPattern,
    debugInfo: {
      serviceName,
      actualUrl,
      expectedPattern,
    },
  };
};

/**
 * 라우팅 오류를 콘솔에 출력 (개발 환경용)
 */
export const logRoutingError = (error: RoutingError): void => {
  if (import.meta.env.DEV) {
    console.group(`🚨 Routing Error: ${error.code}`);
    console.error('Message:', error.message);
    console.warn('Suggestion:', error.suggestion);
    console.log('Requested URL:', error.requestedUrl);
    console.log('Expected Pattern:', error.expectedPattern);
    
    if (error.httpStatus) {
      console.log('HTTP Status:', error.httpStatus);
    }
    
    if (error.debugInfo) {
      console.log('Debug Info:', error.debugInfo);
    }
    
    console.groupEnd();
  }
};

/**
 * 사용자 친화적인 오류 메시지 생성
 */
export const formatUserFriendlyError = (error: RoutingError): string => {
  const baseMessage = error.message;
  
  // 개발 환경에서는 더 자세한 정보 제공
  if (import.meta.env.DEV) {
    return `${baseMessage}\n\n💡 해결 방법: ${error.suggestion}\n\n🔍 요청 URL: ${error.requestedUrl}`;
  }
  
  // 프로덕션 환경에서는 간단한 메시지만 제공
  return baseMessage;
};

/**
 * 라우팅 진단 정보 생성
 */
export const generateRoutingDiagnostics = (): RoutingDiagnostics => {
  const environment = import.meta.env.VITE_DEPLOYMENT_ENV || 'k8s';
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '';
  const apiDomain = import.meta.env.VITE_API_DOMAIN || '';
  
  // 환경 변수 상태
  const environmentVariables = {
    VITE_DEPLOYMENT_ENV: import.meta.env.VITE_DEPLOYMENT_ENV,
    VITE_API_BASE_URL: import.meta.env.VITE_API_BASE_URL,
    VITE_API_DOMAIN: import.meta.env.VITE_API_DOMAIN,
    NODE_ENV: import.meta.env.NODE_ENV,
    DEV: import.meta.env.DEV?.toString(),
    PROD: import.meta.env.PROD?.toString(),
  };
  
  // 서비스별 URL 매핑 (apiConfig.ts의 로직을 참조)
  const serviceUrls: Record<string, string> = {};
  const services = ['auth', 'users', 'boards', 'chat', 'notifications', 'storage'];
  
  services.forEach(service => {
    let baseUrl = '';
    
    switch (environment) {
      case 'docker-compose':
        const portMap: Record<string, string> = {
          auth: '8080',
          users: '8090', 
          boards: '8000',
          chat: '8001',
          notifications: '8002',
          storage: '8003',
        };
        baseUrl = `${apiBaseUrl || 'http://localhost'}:${portMap[service]}/api`;
        break;
        
      case 'k8s':
        baseUrl = `/api/${service}`;
        break;
        
      case 'cloudfront':
        baseUrl = `${apiDomain}/api/${service}`;
        break;
        
      default:
        baseUrl = `/api/${service}`;
    }
    
    serviceUrls[service] = baseUrl;
  });
  
  // 설정 검증 결과
  const validationResults = [
    {
      check: 'Environment Variable Set',
      passed: !!environment,
      message: environment ? `Environment: ${environment}` : 'VITE_DEPLOYMENT_ENV not set',
    },
    {
      check: 'Supported Environment',
      passed: ['docker-compose', 'k8s', 'cloudfront'].includes(environment),
      message: ['docker-compose', 'k8s', 'cloudfront'].includes(environment) 
        ? 'Environment is supported' 
        : `Unsupported environment: ${environment}`,
    },
  ];
  
  // 환경별 추가 검증
  if (environment === 'docker-compose') {
    validationResults.push({
      check: 'Docker Compose API Base URL',
      passed: !!apiBaseUrl,
      message: apiBaseUrl ? `API Base URL: ${apiBaseUrl}` : 'VITE_API_BASE_URL not set for docker-compose',
    });
  }
  
  if (environment === 'cloudfront') {
    validationResults.push({
      check: 'CloudFront API Domain',
      passed: !!apiDomain,
      message: apiDomain ? `API Domain: ${apiDomain}` : 'VITE_API_DOMAIN not set for cloudfront',
    });
  }
  
  return {
    environment,
    serviceUrls,
    environmentVariables,
    validationResults,
  };
};

/**
 * 라우팅 진단 정보를 콘솔에 출력
 */
export const logRoutingDiagnostics = (): void => {
  if (import.meta.env.DEV) {
    const diagnostics = generateRoutingDiagnostics();
    
    console.group('🔍 Routing Diagnostics');
    console.log('Environment:', diagnostics.environment);
    console.log('Service URLs:', diagnostics.serviceUrls);
    console.log('Environment Variables:', diagnostics.environmentVariables);
    
    console.group('Validation Results');
    diagnostics.validationResults.forEach(result => {
      const icon = result.passed ? '✅' : '❌';
      console.log(`${icon} ${result.check}: ${result.message}`);
    });
    console.groupEnd();
    
    console.groupEnd();
  }
};