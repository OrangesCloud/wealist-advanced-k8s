# Error Handling and Logging System

This directory contains the error handling and logging utilities for the frontend API routing system.

## Files

### `errorHandling.ts`
Contains utilities for creating and handling routing errors:
- `createRoutingError()` - Creates structured routing errors from Axios errors
- `createConfigurationError()` - Creates errors for missing environment variables
- `createUrlPatternError()` - Creates errors for URL pattern mismatches
- `logRoutingError()` - Logs routing errors to console (dev only)
- `formatUserFriendlyError()` - Formats errors for user display
- `generateRoutingDiagnostics()` - Generates diagnostic information
- `logRoutingDiagnostics()` - Logs diagnostic information to console

### `apiLogger.ts`
Contains utilities for logging API calls:
- `logApiRequest()` - Logs outgoing API requests
- `logApiResponse()` - Logs successful API responses
- `logApiError()` - Logs API errors with routing error details
- `ApiLogStore` class - Stores and manages API call logs
- Browser debugging functions available at `window.apiLogs`

### `types/errors.ts`
Type definitions for error handling:
- `RoutingError` - Interface for routing error objects
- `ApiCallLog` - Interface for API call log entries
- `RoutingDiagnostics` - Interface for diagnostic information

## Usage

### Automatic Integration
The error handling and logging system is automatically integrated into the API configuration (`apiConfig.ts`). All API calls made through the configured Axios clients will automatically:

1. Log request details (in development)
2. Log response details (in development)
3. Create structured routing errors for 405/404 responses
4. Provide user-friendly error messages
5. Store logs for debugging

### Manual Usage

```typescript
import { createRoutingError, logRoutingError } from '../utils/errorHandling';
import { logApiRequest, apiLogStore } from '../utils/apiLogger';

// Create a routing error manually
const error = createRoutingError(axiosError, requestUrl, serviceName);
logRoutingError(error);

// Access API logs in browser console (development only)
window.apiLogs.getAll();           // Get all logs
window.apiLogs.getErrors();        // Get error logs only
window.apiLogs.getByService('boards'); // Get logs for specific service
window.apiLogs.getStats();         // Get log statistics
```

### Error Types

The system handles several types of routing errors:

- **INVALID_PREFIX** (405 errors) - Wrong service prefix in API calls
- **ROUTING_MISMATCH** (404 errors) - Endpoint not found
- **SERVICE_UNAVAILABLE** (502/503/504 errors) - Service down
- **NETWORK_ERROR** - Connection issues
- **MISSING_CONFIG** - Environment variables not set
- **INVALID_URL_PATTERN** - URL doesn't match expected pattern

### Development Features

In development mode, the system provides:

1. **Console Logging**: Detailed request/response logging with timestamps
2. **Error Diagnostics**: Automatic routing error analysis and suggestions
3. **Configuration Validation**: Environment setup verification
4. **Browser Debugging**: `window.apiLogs` object for interactive debugging
5. **Visual Indicators**: Color-coded console output with emojis

### Production Behavior

In production mode:
- Logging is disabled for performance
- Only essential error information is captured
- User-friendly error messages are simplified
- Debug information is not exposed

## Testing

The system includes comprehensive tests:
- `__tests__/errorHandling.test.ts` - Tests for error handling utilities
- `__tests__/apiLogger.test.ts` - Tests for API logging functionality

Run tests with:
```bash
npm test -- --run utils/__tests__/
```

## Integration with Requirements

This implementation satisfies the following requirements:

- **Requirements 3.2, 5.3**: Clear error messages for 405/404 routing issues
- **Requirements 3.5**: Complete URL logging for debugging
- **Requirements 1.1-1.3**: Environment-specific error handling
- **Requirements 2.1-2.3**: Configuration validation and diagnostics

The system provides comprehensive error handling and logging capabilities while maintaining performance in production and offering rich debugging features in development.