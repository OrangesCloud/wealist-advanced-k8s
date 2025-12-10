package service

import (
	"chat-service/internal/domain"
	"chat-service/internal/repository"
	"context"
	"encoding/json"
	"fmt"
	"sync"

	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
	"go.uber.org/zap"
)

type PresenceService struct {
	repo        *repository.PresenceRepository
	redis       *redis.Client
	logger      *zap.Logger
	onlineUsers map[uuid.UUID]map[uuid.UUID]bool // workspaceID -> userID -> online
	mu          sync.RWMutex
}

func NewPresenceService(
	repo *repository.PresenceRepository,
	redis *redis.Client,
	logger *zap.Logger,
) *PresenceService {
	return &PresenceService{
		repo:        repo,
		redis:       redis,
		logger:      logger,
		onlineUsers: make(map[uuid.UUID]map[uuid.UUID]bool),
	}
}

func (s *PresenceService) SetUserOnline(ctx context.Context, userID, workspaceID uuid.UUID) error {
	// Update in-memory
	s.mu.Lock()
	if s.onlineUsers[workspaceID] == nil {
		s.onlineUsers[workspaceID] = make(map[uuid.UUID]bool)
	}
	s.onlineUsers[workspaceID][userID] = true
	s.mu.Unlock()

	// Update database
	if err := s.repo.SetStatus(userID, workspaceID, domain.PresenceStatusOnline); err != nil {
		s.logger.Error("failed to set user online in DB", zap.Error(err))
	}

	// Broadcast status change
	s.broadcastStatus(ctx, userID, workspaceID, domain.PresenceStatusOnline)

	return nil
}

func (s *PresenceService) SetUserOffline(ctx context.Context, userID, workspaceID uuid.UUID) error {
	// Update in-memory
	s.mu.Lock()
	if s.onlineUsers[workspaceID] != nil {
		delete(s.onlineUsers[workspaceID], userID)
		if len(s.onlineUsers[workspaceID]) == 0 {
			delete(s.onlineUsers, workspaceID)
		}
	}
	s.mu.Unlock()

	// Update database
	if err := s.repo.SetOffline(userID); err != nil {
		s.logger.Error("failed to set user offline in DB", zap.Error(err))
	}

	// Broadcast status change
	s.broadcastStatus(ctx, userID, workspaceID, domain.PresenceStatusOffline)

	return nil
}

func (s *PresenceService) SetUserAway(ctx context.Context, userID, workspaceID uuid.UUID) error {
	if err := s.repo.SetStatus(userID, workspaceID, domain.PresenceStatusAway); err != nil {
		return err
	}

	s.broadcastStatus(ctx, userID, workspaceID, domain.PresenceStatusAway)
	return nil
}

func (s *PresenceService) GetUserStatus(ctx context.Context, userID uuid.UUID) (*domain.UserPresence, error) {
	return s.repo.GetUserStatus(userID)
}

func (s *PresenceService) GetOnlineUsers(ctx context.Context, workspaceID *uuid.UUID) ([]domain.UserPresence, error) {
	return s.repo.GetOnlineUsers(workspaceID)
}

func (s *PresenceService) IsUserOnline(userID, workspaceID uuid.UUID) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()

	if ws, ok := s.onlineUsers[workspaceID]; ok {
		return ws[userID]
	}
	return false
}

func (s *PresenceService) GetOnlineUsersInMemory(workspaceID uuid.UUID) []uuid.UUID {
	s.mu.RLock()
	defer s.mu.RUnlock()

	var users []uuid.UUID
	if ws, ok := s.onlineUsers[workspaceID]; ok {
		for userID := range ws {
			users = append(users, userID)
		}
	}
	return users
}

func (s *PresenceService) broadcastStatus(ctx context.Context, userID, workspaceID uuid.UUID, status domain.PresenceStatus) {
	if s.redis == nil {
		return
	}

	channel := fmt.Sprintf("presence:workspace:%s", workspaceID.String())
	data, err := json.Marshal(map[string]interface{}{
		"type":   "USER_STATUS",
		"userId": userID.String(),
		"status": status,
	})
	if err != nil {
		s.logger.Error("failed to marshal status for broadcast", zap.Error(err))
		return
	}

	if err := s.redis.Publish(ctx, channel, data).Err(); err != nil {
		s.logger.Error("failed to broadcast status", zap.Error(err))
	}
}
