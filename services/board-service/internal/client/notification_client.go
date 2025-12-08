package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"github.com/google/uuid"
	"go.uber.org/zap"
	"project-board-api/internal/metrics"
)

// NotificationType represents the type of notification
type NotificationType string

const (
	NotificationTaskAssigned      NotificationType = "TASK_ASSIGNED"
	NotificationTaskUnassigned    NotificationType = "TASK_UNASSIGNED"
	NotificationTaskMentioned     NotificationType = "TASK_MENTIONED"
	NotificationCommentAdded      NotificationType = "COMMENT_ADDED"
	NotificationCommentMentioned  NotificationType = "COMMENT_MENTIONED"
	NotificationParticipantAdded  NotificationType = "PARTICIPANT_ADDED"
)

// NotificationEvent represents a notification to be sent
type NotificationEvent struct {
	Type         NotificationType       `json:"type"`
	ActorID      uuid.UUID              `json:"actorId"`
	TargetUserID uuid.UUID              `json:"targetUserId"`
	WorkspaceID  uuid.UUID              `json:"workspaceId"`
	ResourceType string                 `json:"resourceType"`
	ResourceID   uuid.UUID              `json:"resourceId"`
	ResourceName string                 `json:"resourceName,omitempty"`
	Metadata     map[string]interface{} `json:"metadata,omitempty"`
	OccurredAt   string                 `json:"occurredAt,omitempty"`
}

// BulkNotificationRequest represents a bulk notification request
type BulkNotificationRequest struct {
	Notifications []NotificationEvent `json:"notifications"`
}

// NotificationClient defines the interface for notification service communication
type NotificationClient interface {
	// SendNotification sends a single notification
	SendNotification(ctx context.Context, event NotificationEvent) error
	// SendBulkNotifications sends multiple notifications at once
	SendBulkNotifications(ctx context.Context, events []NotificationEvent) error
}

// notificationClient implements NotificationClient interface
type notificationClient struct {
	baseURL    string
	apiKey     string
	httpClient *http.Client
	timeout    time.Duration
	logger     *zap.Logger
	metrics    *metrics.Metrics
}

// NewNotificationClient creates a new Notification API client
func NewNotificationClient(baseURL string, apiKey string, timeout time.Duration, logger *zap.Logger, m *metrics.Metrics) NotificationClient {
	return &notificationClient{
		baseURL: baseURL,
		apiKey:  apiKey,
		httpClient: &http.Client{
			Timeout: timeout,
		},
		timeout: timeout,
		logger:  logger,
		metrics: m,
	}
}

// SendNotification sends a single notification to the notification service
func (c *notificationClient) SendNotification(ctx context.Context, event NotificationEvent) error {
	url := fmt.Sprintf("%s/api/internal/notifications", c.baseURL)

	// Set occurred time if not provided
	if event.OccurredAt == "" {
		event.OccurredAt = time.Now().UTC().Format(time.RFC3339)
	}

	jsonBody, err := json.Marshal(event)
	if err != nil {
		c.logger.Error("Failed to marshal notification event",
			zap.Error(err),
			zap.String("type", string(event.Type)),
		)
		return fmt.Errorf("failed to marshal notification: %w", err)
	}

	startTime := time.Now()

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(jsonBody))
	if err != nil {
		c.logger.Error("Failed to create notification request", zap.Error(err))
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-API-Key", c.apiKey)

	resp, err := c.httpClient.Do(req)
	duration := time.Since(startTime)

	// Record metrics
	statusCode := 0
	if resp != nil {
		statusCode = resp.StatusCode
	}
	if c.metrics != nil {
		c.metrics.RecordExternalAPICall(url, "POST", statusCode, duration, err)
	}

	if err != nil {
		c.logger.Error("Failed to send notification",
			zap.Error(err),
			zap.String("type", string(event.Type)),
			zap.Duration("duration", duration),
		)
		// Graceful degradation: log error but don't fail the main operation
		return nil
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 200 && resp.StatusCode < 300 {
		c.logger.Info("Notification sent successfully",
			zap.String("type", string(event.Type)),
			zap.String("target_user_id", event.TargetUserID.String()),
			zap.Duration("duration", duration),
		)
		return nil
	}

	c.logger.Warn("Notification service returned non-success status",
		zap.Int("status_code", resp.StatusCode),
		zap.String("type", string(event.Type)),
		zap.Duration("duration", duration),
	)

	// Graceful degradation: don't fail the main operation
	return nil
}

// SendBulkNotifications sends multiple notifications at once
func (c *notificationClient) SendBulkNotifications(ctx context.Context, events []NotificationEvent) error {
	if len(events) == 0 {
		return nil
	}

	url := fmt.Sprintf("%s/api/internal/notifications/bulk", c.baseURL)

	// Set occurred time for events that don't have it
	now := time.Now().UTC().Format(time.RFC3339)
	for i := range events {
		if events[i].OccurredAt == "" {
			events[i].OccurredAt = now
		}
	}

	request := BulkNotificationRequest{Notifications: events}
	jsonBody, err := json.Marshal(request)
	if err != nil {
		c.logger.Error("Failed to marshal bulk notification request",
			zap.Error(err),
			zap.Int("count", len(events)),
		)
		return fmt.Errorf("failed to marshal notifications: %w", err)
	}

	startTime := time.Now()

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(jsonBody))
	if err != nil {
		c.logger.Error("Failed to create bulk notification request", zap.Error(err))
		return fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("X-Internal-API-Key", c.apiKey)

	resp, err := c.httpClient.Do(req)
	duration := time.Since(startTime)

	// Record metrics
	statusCode := 0
	if resp != nil {
		statusCode = resp.StatusCode
	}
	if c.metrics != nil {
		c.metrics.RecordExternalAPICall(url, "POST", statusCode, duration, err)
	}

	if err != nil {
		c.logger.Error("Failed to send bulk notifications",
			zap.Error(err),
			zap.Int("count", len(events)),
			zap.Duration("duration", duration),
		)
		// Graceful degradation
		return nil
	}
	defer resp.Body.Close()

	if resp.StatusCode >= 200 && resp.StatusCode < 300 {
		c.logger.Info("Bulk notifications sent successfully",
			zap.Int("count", len(events)),
			zap.Duration("duration", duration),
		)
		return nil
	}

	c.logger.Warn("Notification service returned non-success status for bulk request",
		zap.Int("status_code", resp.StatusCode),
		zap.Int("count", len(events)),
		zap.Duration("duration", duration),
	)

	// Graceful degradation
	return nil
}

// NoOpNotificationClient is a no-op implementation for when notifications are disabled
type NoOpNotificationClient struct{}

func NewNoOpNotificationClient() NotificationClient {
	return &NoOpNotificationClient{}
}

func (c *NoOpNotificationClient) SendNotification(ctx context.Context, event NotificationEvent) error {
	return nil
}

func (c *NoOpNotificationClient) SendBulkNotifications(ctx context.Context, events []NotificationEvent) error {
	return nil
}
