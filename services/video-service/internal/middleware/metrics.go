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

	// Video call specific metrics
	videoRoomsActive = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "video_rooms_active",
			Help: "Number of active video rooms",
		},
	)

	videoParticipantsTotal = promauto.NewCounter(
		prometheus.CounterOpts{
			Name: "video_participants_total",
			Help: "Total number of video call participants",
		},
	)

	videoCallDuration = promauto.NewHistogramVec(
		prometheus.HistogramOpts{
			Name:    "video_call_duration_seconds",
			Help:    "Video call duration in seconds",
			Buckets: []float64{60, 300, 600, 1800, 3600, 7200},
		},
		[]string{"room_type"},
	)

	// =========================================================================
	// Business Metrics - 비디오 서비스 전용
	// =========================================================================

	// 일일 비디오 사용자 수 (유니크)
	videoDailyActiveUsers = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "video_daily_active_users",
			Help: "Number of unique users who used video today",
		},
	)

	// 현재 진행 중인 참가자 수
	videoParticipantsActive = promauto.NewGauge(
		prometheus.GaugeOpts{
			Name: "video_participants_active",
			Help: "Number of currently active video participants",
		},
	)

	// 비디오 통화 시작 카운터
	videoCallsStartedTotal = promauto.NewCounter(
		prometheus.CounterOpts{
			Name: "video_calls_started_total",
			Help: "Total number of video calls started",
		},
	)

	// 비디오 통화 종료 카운터
	videoCallsEndedTotal = promauto.NewCounter(
		prometheus.CounterOpts{
			Name: "video_calls_ended_total",
			Help: "Total number of video calls ended",
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

// RecordRoomCreated increments active rooms
func RecordRoomCreated() {
	videoRoomsActive.Inc()
}

// RecordRoomEnded decrements active rooms
func RecordRoomEnded() {
	videoRoomsActive.Dec()
}

// RecordParticipant increments total participants
func RecordParticipant() {
	videoParticipantsTotal.Inc()
}

// RecordCallDuration records call duration
func RecordCallDuration(roomType string, durationSeconds float64) {
	videoCallDuration.WithLabelValues(roomType).Observe(durationSeconds)
}

// =============================================================================
// Business Metrics Helper Functions
// =============================================================================

// SetDailyActiveUsers sets the number of unique daily video users
func SetDailyActiveUsers(count float64) {
	videoDailyActiveUsers.Set(count)
}

// SetActiveParticipants sets the current number of active participants
func SetActiveParticipants(count float64) {
	videoParticipantsActive.Set(count)
}

// RecordParticipantJoined increments active and total participants
func RecordParticipantJoined() {
	videoParticipantsTotal.Inc()
	videoParticipantsActive.Inc()
}

// RecordParticipantLeft decrements active participants
func RecordParticipantLeft() {
	videoParticipantsActive.Dec()
}

// RecordCallStarted increments the call started counter
func RecordCallStarted() {
	videoCallsStartedTotal.Inc()
}

// RecordCallEnded increments the call ended counter
func RecordCallEnded() {
	videoCallsEndedTotal.Inc()
}
