package middleware

import (
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

var (
	// HTTP request counter
	httpRequestsTotal = promauto.NewCounterVec(
		prometheus.CounterOpts{
			Name: "http_requests_total",
			Help: "Total number of HTTP requests",
		},
		[]string{"method", "path", "status"},
	)

	// HTTP request duration histogram
	httpRequestDuration = promauto.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "http_request_duration_seconds",
			Help:    "HTTP request duration in seconds",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"method", "path"},
	)

	// HTTP request size histogram
	httpRequestSize = promauto.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "http_request_size_bytes",
			Help:    "HTTP request size in bytes",
			Buckets: prometheus.ExponentialBuckets(100, 10, 8),
		},
		[]string{"method", "path"},
	)

	// HTTP response size histogram
	httpResponseSize = promauto.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "http_response_size_bytes",
			Help:    "HTTP response size in bytes",
			Buckets: prometheus.ExponentialBuckets(100, 10, 8),
		},
		[]string{"method", "path"},
	)

	// Active requests gauge
	httpRequestsInFlight = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "http_requests_in_flight",
			Help: "Number of HTTP requests currently being processed",
		},
	)

	// =========================================================================
	// Business Metrics - 사용자 서비스 전용
	// =========================================================================

	// 신규 회원 가입 카운터
	userRegistrationsTotal = promauto.NewCounter(
		prometheus.CounterOpts{
			Name: "user_registrations_total",
			Help: "Total number of user registrations",
		},
	)

	// 로그인 카운터 (DAU/MAU 추정용)
	userLoginsTotal = promauto.NewCounter(
		prometheus.CounterOpts{
			Name: "user_logins_total",
			Help: "Total number of user logins",
		},
	)

	// 활성 사용자 수 (현재 동시 접속자)
	usersActiveTotal = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "users_active_total",
			Help: "Number of currently active users (concurrent sessions)",
		},
	)

	// 전체 등록 사용자 수
	usersRegisteredTotal = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "users_registered_total",
			Help: "Total number of registered users",
		},
	)

	// 워크스페이스 생성 카운터
	workspacesCreatedTotal = promauto.NewCounter(
		prometheus.CounterOpts{
			Name: "workspaces_created_total",
			Help: "Total number of workspaces created",
		},
	)

	// 전체 워크스페이스 수
	workspacesTotal = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "workspaces_total",
			Help: "Total number of active workspaces",
		},
	)

	// 워크스페이스 멤버 수
	workspaceMembersTotal = promauto.NewGaugeVec(
		prometheus.GaugeOpts{
			Name: "workspace_members_total",
			Help: "Number of members per workspace",
		},
		[]string{"workspace_id"},
	)
)

// Metrics returns a Gin middleware that collects Prometheus metrics
func Metrics() gin.HandlerFunc {
	return func(c *gin.Context) {
		// Skip metrics endpoint itself
		if c.Request.URL.Path == "/metrics" {
			c.Next()
			return
		}

		start := time.Now()
		httpRequestsInFlight.Inc()

		// Get request size
		requestSize := float64(c.Request.ContentLength)
		if requestSize < 0 {
			requestSize = 0
		}

		c.Next()

		httpRequestsInFlight.Dec()
		duration := time.Since(start).Seconds()

		// Normalize path to avoid high cardinality (replace IDs with :id)
		path := c.FullPath()
		if path == "" {
			path = c.Request.URL.Path
		}

		status := strconv.Itoa(c.Writer.Status())
		method := c.Request.Method

		// Record metrics
		httpRequestsTotal.WithLabelValues(method, path, status).Inc()
		httpRequestDuration.WithLabelValues(method, path).Observe(duration)
		httpRequestSize.WithLabelValues(method, path).Observe(requestSize)
		httpResponseSize.WithLabelValues(method, path).Observe(float64(c.Writer.Size()))
	}
}

// MetricsHandler returns the Prometheus metrics handler for Gin
func MetricsHandler() gin.HandlerFunc {
	h := promhttp.Handler()
	return func(c *gin.Context) {
		h.ServeHTTP(c.Writer, c.Request)
	}
}

// =============================================================================
// Business Metrics Helper Functions
// =============================================================================

// RecordUserRegistration increments the user registration counter
func RecordUserRegistration() {
	userRegistrationsTotal.Inc()
}

// RecordUserLogin increments the login counter (for DAU/MAU estimation)
func RecordUserLogin() {
	userLoginsTotal.Inc()
}

// SetActiveUsers sets the current number of active users
func SetActiveUsers(count float64) {
	usersActiveTotal.Set(count)
}

// IncrementActiveUsers increases active user count by 1
func IncrementActiveUsers() {
	usersActiveTotal.Inc()
}

// DecrementActiveUsers decreases active user count by 1
func DecrementActiveUsers() {
	usersActiveTotal.Dec()
}

// SetRegisteredUsers sets the total number of registered users
func SetRegisteredUsers(count float64) {
	usersRegisteredTotal.Set(count)
}

// RecordWorkspaceCreation increments the workspace creation counter
func RecordWorkspaceCreation() {
	workspacesCreatedTotal.Inc()
}

// SetTotalWorkspaces sets the total number of workspaces
func SetTotalWorkspaces(count float64) {
	workspacesTotal.Set(count)
}

// SetWorkspaceMembers sets the member count for a specific workspace
func SetWorkspaceMembers(workspaceID string, count float64) {
	workspaceMembersTotal.WithLabelValues(workspaceID).Set(count)
}
