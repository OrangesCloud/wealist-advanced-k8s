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
	httpRequestsTotal = promauto.NewCounterVec(
		prometheus.CounterOpts{
			Name: "http_requests_total",
			Help: "Total number of HTTP requests",
		},
		[]string{"method", "path", "status"},
	)

	httpRequestDuration = promauto.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "http_request_duration_seconds",
			Help:    "HTTP request duration in seconds",
			Buckets: prometheus.DefBuckets,
		},
		[]string{"method", "path"},
	)

	httpRequestsInFlight = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "http_requests_in_flight",
			Help: "Number of HTTP requests currently being processed",
		},
	)

	// WebSocket specific metrics
	wsConnectionsTotal = promauto.NewCounter(
		prometheus.CounterOpts{
			Name: "websocket_connections_total",
			Help: "Total number of WebSocket connections",
		},
	)

	wsActiveConnections = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "websocket_active_connections",
			Help: "Number of active WebSocket connections",
		},
	)

	// =========================================================================
	// Business Metrics - 채팅 서비스 전용
	// =========================================================================

	// 일일 채팅 사용자 수 (유니크)
	chatDailyActiveUsers = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "chat_daily_active_users",
			Help: "Number of unique users who used chat today",
		},
	)

	// 채팅 메시지 전송 카운터
	chatMessagesSentTotal = promauto.NewCounter(
		prometheus.CounterOpts{
			Name: "chat_messages_sent_total",
			Help: "Total number of chat messages sent",
		},
	)

	// 채팅방 생성 카운터
	chatRoomsCreatedTotal = promauto.NewCounter(
		prometheus.CounterOpts{
			Name: "chat_rooms_created_total",
			Help: "Total number of chat rooms created",
		},
	)

	// 활성 채팅방 수
	chatRoomsActive = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "chat_rooms_active",
			Help: "Number of active chat rooms",
		},
	)
)

// MetricsMiddleware returns a Gin middleware that collects Prometheus metrics
func MetricsMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		if c.Request.URL.Path == "/metrics" {
			c.Next()
			return
		}

		start := time.Now()
		httpRequestsInFlight.Inc()

		c.Next()

		httpRequestsInFlight.Dec()
		duration := time.Since(start).Seconds()

		path := c.FullPath()
		if path == "" {
			path = c.Request.URL.Path
		}

		status := strconv.Itoa(c.Writer.Status())
		method := c.Request.Method

		httpRequestsTotal.WithLabelValues(method, path, status).Inc()
		httpRequestDuration.WithLabelValues(method, path).Observe(duration)
	}
}

// MetricsHandler returns the Prometheus metrics handler for Gin
func MetricsHandler() gin.HandlerFunc {
	h := promhttp.Handler()
	return func(c *gin.Context) {
		h.ServeHTTP(c.Writer, c.Request)
	}
}

// RecordWebSocketConnection increments WebSocket connection counters
func RecordWebSocketConnection() {
	wsConnectionsTotal.Inc()
	wsActiveConnections.Inc()
}

// RecordWebSocketDisconnection decrements active WebSocket connection gauge
func RecordWebSocketDisconnection() {
	wsActiveConnections.Dec()
}

// =============================================================================
// Business Metrics Helper Functions
// =============================================================================

// SetDailyActiveUsers sets the number of unique daily chat users
func SetDailyActiveUsers(count float64) {
	chatDailyActiveUsers.Set(count)
}

// RecordMessageSent increments the chat message counter
func RecordMessageSent() {
	chatMessagesSentTotal.Inc()
}

// RecordRoomCreated increments the chat room creation counter
func RecordRoomCreated() {
	chatRoomsCreatedTotal.Inc()
	chatRoomsActive.Inc()
}

// RecordRoomDeleted decrements active rooms
func RecordRoomDeleted() {
	chatRoomsActive.Dec()
}

// SetActiveRooms sets the number of active chat rooms
func SetActiveRooms(count float64) {
	chatRoomsActive.Set(count)
}
