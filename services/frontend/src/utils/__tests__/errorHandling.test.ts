// Test file for error handling utilities
import { describe, it, expect, vi } from 'vitest';
import { AxiosError } from 'axios';
import {
  createRoutingError,
  createConfigurationError,
  createUrlPatternError,
  formatUserFriendlyError,
  generateRoutingDiagnostics,
} from '../errorHandling';

// Mock import.meta.env
vi.mock('import.meta.env', () => ({
  DEV: true,
  VITE_DEPLOYMENT_ENV: 'k8s',
  VITE_API_BASE_URL: '',
  VITE_API_DOMAIN: 'https://api.test.com',
}));

describe('Error Handling Utilities', () => {
  describe('createRoutingError', () => {
    it('should create routing error for 405 status', () => {
      const mockError = {
        response: { status: 405 },
        config: { method: 'GET', baseURL: '/api/boards', url: '/projects' },
        message: 'Method not allowed',
      } as AxiosError;

      const error = createRoutingError(mockError, '/api/boards/projects', 'boards');

      expect(error.code).toBe('INVALID_PREFIX');
      expect(error.message).toContain('boards 서비스 API 호출이 잘못된 경로로 라우팅되었습니다');
      expect(error.suggestion).toContain('/api/boards prefix를 사용하는지 확인해주세요');
      expect(error.expectedPattern).toBe('/api/boards/*');
      expect(error.httpStatus).toBe(405);
    });

    it('should create routing error for 404 status', () => {
      const mockError = {
        response: { status: 404 },
        config: { method: 'POST', baseURL: '/api/chat', url: '/messages' },
        message: 'Not found',
      } as AxiosError;

      const error = createRoutingError(mockError, '/api/chat/messages', 'chat');

      expect(error.code).toBe('ROUTING_MISMATCH');
      expect(error.message).toContain('요청한 엔드포인트를 찾을 수 없습니다');
      expect(error.expectedPattern).toBe('/api/chat/*');
      expect(error.httpStatus).toBe(404);
    });

    it('should create routing error for service unavailable status', () => {
      const mockError = {
        response: { status: 503 },
        config: { method: 'GET', baseURL: '/api/storage', url: '/files' },
        message: 'Service unavailable',
      } as AxiosError;

      const error = createRoutingError(mockError, '/api/storage/files', 'storage');

      expect(error.code).toBe('SERVICE_UNAVAILABLE');
      expect(error.message).toContain('storage 서비스에 연결할 수 없습니다');
      expect(error.suggestion).toContain('서비스가 정상적으로 실행 중인지 확인');
    });

    it('should create network error for no response', () => {
      const mockError = {
        config: { method: 'GET', baseURL: '/api/auth', url: '/login' },
        message: 'Network Error',
        code: 'NETWORK_ERROR',
      } as AxiosError;

      const error = createRoutingError(mockError, '/api/auth/login', 'auth');

      expect(error.code).toBe('NETWORK_ERROR');
      expect(error.message).toContain('네트워크 연결 오류가 발생했습니다');
      expect(error.suggestion).toContain('인터넷 연결을 확인하고 다시 시도해주세요');
    });
  });

  describe('createConfigurationError', () => {
    it('should create configuration error for missing variable', () => {
      const error = createConfigurationError('VITE_API_BASE_URL', 'docker-compose');

      expect(error.code).toBe('MISSING_CONFIG');
      expect(error.message).toContain('VITE_API_BASE_URL가 설정되지 않았습니다');
      expect(error.suggestion).toContain('docker-compose 환경에 맞는 VITE_API_BASE_URL 값을 설정해주세요');
      expect(error.expectedPattern).toBe('VITE_API_BASE_URL=<적절한 값>');
    });
  });

  describe('createUrlPatternError', () => {
    it('should create URL pattern error', () => {
      const error = createUrlPatternError(
        'http://localhost:8000/projects',
        '/api/boards/*',
        'boards'
      );

      expect(error.code).toBe('INVALID_URL_PATTERN');
      expect(error.message).toContain('생성된 URL이 예상 패턴과 일치하지 않습니다');
      expect(error.suggestion).toContain('boards 서비스의 API 설정을 확인');
      expect(error.requestedUrl).toBe('http://localhost:8000/projects');
      expect(error.expectedPattern).toBe('/api/boards/*');
    });
  });

  describe('formatUserFriendlyError', () => {
    it('should format error for development environment', () => {
      const mockError = {
        code: 'INVALID_PREFIX' as const,
        message: 'Test error message',
        suggestion: 'Test suggestion',
        requestedUrl: '/test/url',
        expectedPattern: '/api/test/*',
      };

      const formatted = formatUserFriendlyError(mockError);

      expect(formatted).toContain('Test error message');
      expect(formatted).toContain('💡 해결 방법: Test suggestion');
      expect(formatted).toContain('🔍 요청 URL: /test/url');
    });
  });

  describe('generateRoutingDiagnostics', () => {
    it('should generate routing diagnostics', () => {
      const diagnostics = generateRoutingDiagnostics();

      expect(diagnostics.environment).toBe('k8s');
      expect(diagnostics.serviceUrls).toHaveProperty('auth');
      expect(diagnostics.serviceUrls).toHaveProperty('boards');
      expect(diagnostics.serviceUrls).toHaveProperty('storage');
      expect(diagnostics.environmentVariables).toHaveProperty('VITE_DEPLOYMENT_ENV');
      expect(diagnostics.validationResults).toBeInstanceOf(Array);
      expect(diagnostics.validationResults.length).toBeGreaterThan(0);
    });

    it('should validate environment configuration', () => {
      const diagnostics = generateRoutingDiagnostics();
      
      const envCheck = diagnostics.validationResults.find(
        result => result.check === 'Environment Variable Set'
      );
      expect(envCheck).toBeDefined();
      expect(envCheck?.passed).toBe(true);

      const supportedEnvCheck = diagnostics.validationResults.find(
        result => result.check === 'Supported Environment'
      );
      expect(supportedEnvCheck).toBeDefined();
      expect(supportedEnvCheck?.passed).toBe(true);
    });
  });
});