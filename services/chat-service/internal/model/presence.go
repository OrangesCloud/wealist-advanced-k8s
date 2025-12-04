// internal/model/presence.go
package model

import (
	"time"

	"github.com/google/uuid"
)

type PresenceStatus string

const (
	PresenceOnline  PresenceStatus = "ONLINE"
	PresenceAway    PresenceStatus = "AWAY"
	PresenceOffline PresenceStatus = "OFFLINE"
)

type UserPresence struct {
	UserID      uuid.UUID      `gorm:"type:uuid;primaryKey" json:"userId"`
	WorkspaceID uuid.UUID      `gorm:"type:uuid;not null;index:idx_workspace_status" json:"workspaceId"`
	Status      PresenceStatus `gorm:"type:varchar(20);default:'ONLINE';index:idx_workspace_status" json:"status"`
	LastSeen    time.Time      `gorm:"autoCreateTime" json:"lastSeen"`
}

func (UserPresence) TableName() string {
	return "user_presence"
}