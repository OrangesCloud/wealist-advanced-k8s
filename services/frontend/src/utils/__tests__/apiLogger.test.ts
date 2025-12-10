// Test file for API logging utilities
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AxiosRequestConfig, AxiosResponse, AxiosError } from 'axios';
import { logApiRequest, logApiResponse, logApiError, apiLogStore } from '../apiLogger';

// Mock import.meta.env
vi.mock('import.meta.env', () => ({
  DEV: true,
}));

// Mock console methods
const mockConsole = {
  log: vi.fn(),
  error: vi.fn(),
  warn: vi.fn(),
  group: vi.fn(),
  groupEnd: vi.fn(),
};

vi.stubGlobal('console', mockConsole);

describe('API Logger Utilities', () => {
  beforeEach(() => {
    // Clear logs before each test
    apiLogStore.clearLogs();
    vi.clearAllMocks();
  });

  describe('logApiRequest', () => {
    it('should log API request in development environment', () => {
      const mockConfig: AxiosRequestConfig = {
        method: 'GET',
        baseURL: '/api/boards',
        url: '/projects',
        headers: { 'Content-Type': 'application/json' },
      };

      logApiRequest(mockConfig);

      expect(mockConsole.log).toHaveBeenCalledWith(
        expect.stringContaining('🚀 [BOARDS] GET /api/boards/projects'),
        expect.any(Object)
      );

      const logs = apiLogStore.getAllLogs();
      expect(logs).toHaveLength(1);
      expect(logs[0].serviceName).toBe('boards');
      expect(logs[0].method).toBe('GET');
      expect(logs[0].fullUrl).toBe('/api/boards/projects');
    });

    it('should extract service name from port number', () => {
      const mockConfig: AxiosRequestConfig = {
        method: 'POST',
        baseURL: 'http://localhost:8000',
        url: '/projects',
      };

      logApiRequest(mockConfig);

      const logs = apiLogStore.getAllLogs();
      expect(logs[0].serviceName).toBe('boards');
    });

    it('should handle query parameters in URL', () => {
      const mockConfig: AxiosRequestConfig = {
        method: 'GET',
        baseURL: '/api/storage',
        url: '/files',
        params: { workspaceId: '123', folderId: '456' },
      };

      logApiRequest(mockConfig);

      const logs = apiLogStore.getAllLogs();
      expect(logs[0].fullUrl).toContain('workspaceId=123');
      expect(logs[0].fullUrl).toContain('folderId=456');
    });
  });

  describe('logApiResponse', () => {
    it('should log successful API response', () => {
      const mockResponse: AxiosResponse = {
        status: 200,
        data: { success: true },
        headers: {},
        config: {
          method: 'GET',
          baseURL: '/api/chat',
          url: '/messages',
          headers: {},
        } as any,
        statusText: 'OK',
      };

      const startTime = Date.now() - 100;
      logApiResponse(mockResponse, startTime);

      expect(mockConsole.log).toHaveBeenCalledWith(
        expect.stringContaining('✅ [CHAT] 200 GET /api/chat/messages'),
        expect.any(Object)
      );

      const logs = apiLogStore.getAllLogs();
      expect(logs).toHaveLength(1);
      expect(logs[0].statusCode).toBe(200);
      expect(logs[0].responseTime).toBeGreaterThan(0);
    });

    it('should log warning for 4xx responses', () => {
      const mockResponse: AxiosResponse = {
        status: 404,
        data: { error: 'Not found' },
        headers: {},
        config: {
          method: 'GET',
          baseURL: '/api/users',
          url: '/profile',
          headers: {},
        } as any,
        statusText: 'Not Found',
      };

      logApiResponse(mockResponse);

      expect(mockConsole.log).toHaveBeenCalledWith(
        expect.stringContaining('⚠️ [USERS] 404 GET /api/users/profile'),
        expect.any(Object)
      );
    });
  });

  describe('logApiError', () => {
    it('should log API error with routing error details', () => {
      const mockError: AxiosError = {
        response: {
          status: 405,
          data: { error: 'Method not allowed' },
          headers: {},
          config: {
            method: 'POST',
            baseURL: '/api/notifications',
            url: '/send',
            headers: {},
          } as any,
          statusText: 'Method Not Allowed',
        },
        config: {
          method: 'POST',
          baseURL: '/api/notifications',
          url: '/send',
          headers: {},
        } as any,
        message: 'Request failed with status code 405',
        name: 'AxiosError',
        isAxiosError: true,
        toJSON: () => ({}),
      };

      const startTime = Date.now() - 200;
      logApiError(mockError, startTime);

      expect(mockConsole.error).toHaveBeenCalledWith(
        expect.stringContaining('❌ [NOTIFICATIONS] 405 POST /api/notifications/send'),
        expect.any(Object)
      );

      const logs = apiLogStore.getAllLogs();
      expect(logs).toHaveLength(1);
      expect(logs[0].error).toBeDefined();
      expect(logs[0].error?.code).toBe('INVALID_PREFIX');
      expect(logs[0].statusCode).toBe(405);
    });

    it('should handle network errors without response', () => {
      const mockError: AxiosError = {
        config: {
          method: 'GET',
          baseURL: '/api/auth',
          url: '/status',
          headers: {},
        } as any,
        message: 'Network Error',
        name: 'AxiosError',
        code: 'NETWORK_ERROR',
        isAxiosError: true,
        toJSON: () => ({}),
      };

      logApiError(mockError);

      expect(mockConsole.error).toHaveBeenCalledWith(
        expect.stringContaining('❌ [AUTH] NETWORK GET /api/auth/status'),
        expect.any(Object)
      );

      const logs = apiLogStore.getAllLogs();
      expect(logs[0].error?.code).toBe('NETWORK_ERROR');
    });
  });

  describe('ApiLogStore', () => {
    it('should store and retrieve logs correctly', () => {
      const mockLog = {
        serviceName: 'test',
        method: 'GET',
        fullUrl: '/api/test/endpoint',
        timestamp: new Date().toISOString(),
        statusCode: 200,
      };

      apiLogStore.addLog(mockLog);

      const allLogs = apiLogStore.getAllLogs();
      expect(allLogs).toHaveLength(1);
      expect(allLogs[0]).toEqual(mockLog);
    });

    it('should filter logs by service', () => {
      apiLogStore.addLog({
        serviceName: 'boards',
        method: 'GET',
        fullUrl: '/api/boards/projects',
        timestamp: new Date().toISOString(),
      });

      apiLogStore.addLog({
        serviceName: 'chat',
        method: 'POST',
        fullUrl: '/api/chat/messages',
        timestamp: new Date().toISOString(),
      });

      const boardsLogs = apiLogStore.getLogsByService('boards');
      expect(boardsLogs).toHaveLength(1);
      expect(boardsLogs[0].serviceName).toBe('boards');

      const chatLogs = apiLogStore.getLogsByService('chat');
      expect(chatLogs).toHaveLength(1);
      expect(chatLogs[0].serviceName).toBe('chat');
    });

    it('should filter error logs', () => {
      apiLogStore.addLog({
        serviceName: 'storage',
        method: 'GET',
        fullUrl: '/api/storage/files',
        timestamp: new Date().toISOString(),
        statusCode: 200,
      });

      apiLogStore.addLog({
        serviceName: 'storage',
        method: 'POST',
        fullUrl: '/api/storage/upload',
        timestamp: new Date().toISOString(),
        statusCode: 500,
        error: {
          code: 'SERVICE_UNAVAILABLE',
          message: 'Service error',
          suggestion: 'Try again',
          requestedUrl: '/api/storage/upload',
          expectedPattern: '/api/storage/*',
        },
      });

      const errorLogs = apiLogStore.getErrorLogs();
      expect(errorLogs).toHaveLength(1);
      expect(errorLogs[0].error).toBeDefined();
    });

    it('should generate log statistics', () => {
      // Add multiple logs
      apiLogStore.addLog({
        serviceName: 'boards',
        method: 'GET',
        fullUrl: '/api/boards/projects',
        timestamp: new Date().toISOString(),
        statusCode: 200,
      });

      apiLogStore.addLog({
        serviceName: 'boards',
        method: 'POST',
        fullUrl: '/api/boards/create',
        timestamp: new Date().toISOString(),
        statusCode: 201,
      });

      apiLogStore.addLog({
        serviceName: 'chat',
        method: 'GET',
        fullUrl: '/api/chat/messages',
        timestamp: new Date().toISOString(),
        statusCode: 404,
        error: {
          code: 'ROUTING_MISMATCH',
          message: 'Not found',
          suggestion: 'Check URL',
          requestedUrl: '/api/chat/messages',
          expectedPattern: '/api/chat/*',
        },
      });

      const stats = apiLogStore.getLogStats();
      expect(stats.total).toBe(3);
      expect(stats.errors).toBe(1);
      expect(stats.byService.boards).toBe(2);
      expect(stats.byService.chat).toBe(1);
      expect(stats.byStatus['2xx']).toBe(2);
      expect(stats.byStatus['4xx']).toBe(1);
    });

    it('should limit log storage to maximum count', () => {
      // Add more than maxLogs (100) entries
      for (let i = 0; i < 105; i++) {
        apiLogStore.addLog({
          serviceName: 'test',
          method: 'GET',
          fullUrl: `/api/test/endpoint${i}`,
          timestamp: new Date().toISOString(),
        });
      }

      const logs = apiLogStore.getAllLogs();
      expect(logs.length).toBeLessThanOrEqual(100);
      
      // Should keep the most recent logs
      expect(logs[0].fullUrl).toBe('/api/test/endpoint104');
    });
  });
});