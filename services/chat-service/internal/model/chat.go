// internal/model/chat.go
package model

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

type ChatType string

const (
	ChatTypeDM      ChatType = "DM"
	ChatTypeGroup   ChatType = "GROUP"
	ChatTypeProject ChatType = "PROJECT"
)

// Chat represents a chat room
// @Description Chat room model
type Chat struct {
	ChatID      uuid.UUID       `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"chatId"`
	WorkspaceID uuid.UUID       `gorm:"type:uuid;not null;index" json:"workspaceId"`
	ProjectID   *uuid.UUID      `gorm:"type:uuid;index" json:"projectId,omitempty"`
	ChatType    ChatType        `gorm:"type:varchar(20);not null" json:"chatType"`
	ChatName    string          `gorm:"type:varchar(100)" json:"chatName,omitempty"`
	CreatedBy   uuid.UUID       `gorm:"type:uuid;not null" json:"createdBy"`
	CreatedAt   time.Time       `gorm:"autoCreateTime" json:"createdAt"`
	UpdatedAt   time.Time       `gorm:"autoUpdateTime" json:"updatedAt"`
	DeletedAt   gorm.DeletedAt  `gorm:"index" json:"deletedAt,omitempty"`
	Participants []ChatParticipant `gorm:"foreignKey:ChatID" json:"participants,omitempty"`
	Messages    []Message       `gorm:"foreignKey:ChatID" json:"messages,omitempty"`
}

type ChatParticipant struct {
	ParticipantID uuid.UUID      `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"participantId"`
	ChatID        uuid.UUID      `gorm:"type:uuid;not null;index:idx_chat_user" json:"chatId"`
	UserID        uuid.UUID      `gorm:"type:uuid;not null;index:idx_chat_user,idx_user" json:"userId"`
	JoinedAt      time.Time      `gorm:"autoCreateTime" json:"joinedAt"`
	LastReadAt    time.Time      `gorm:"autoCreateTime" json:"lastReadAt"`
	IsActive      bool           `gorm:"default:true" json:"isActive"`
}

func (Chat) TableName() string {
	return "chats"
}

func (ChatParticipant) TableName() string {
	return "chat_participants"
}