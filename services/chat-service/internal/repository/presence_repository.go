package repository

import (
	"chat-service/internal/domain"
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

type PresenceRepository struct {
	db *gorm.DB
}

func NewPresenceRepository(db *gorm.DB) *PresenceRepository {
	return &PresenceRepository{db: db}
}

func (r *PresenceRepository) SetStatus(userID, workspaceID uuid.UUID, status domain.PresenceStatus) error {
	presence := &domain.UserPresence{
		UserID:      userID,
		WorkspaceID: workspaceID,
		Status:      status,
		LastSeen:    time.Now(),
	}

	return r.db.Clauses(clause.OnConflict{
		Columns:   []clause.Column{{Name: "user_id"}},
		DoUpdates: clause.AssignmentColumns([]string{"status", "last_seen", "workspace_id"}),
	}).Create(presence).Error
}

func (r *PresenceRepository) GetUserStatus(userID uuid.UUID) (*domain.UserPresence, error) {
	var presence domain.UserPresence
	err := r.db.First(&presence, "user_id = ?", userID).Error
	if err != nil {
		return nil, err
	}
	return &presence, nil
}

func (r *PresenceRepository) GetOnlineUsers(workspaceID *uuid.UUID) ([]domain.UserPresence, error) {
	var presences []domain.UserPresence
	query := r.db.Where("status = ?", domain.PresenceStatusOnline)

	if workspaceID != nil {
		query = query.Where("workspace_id = ?", workspaceID)
	}

	err := query.Find(&presences).Error
	return presences, err
}

func (r *PresenceRepository) SetOffline(userID uuid.UUID) error {
	return r.db.Model(&domain.UserPresence{}).
		Where("user_id = ?", userID).
		Updates(map[string]interface{}{
			"status":    domain.PresenceStatusOffline,
			"last_seen": time.Now(),
		}).Error
}
