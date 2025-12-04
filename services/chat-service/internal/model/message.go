// internal/model/message.go
package model

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

type MessageType string

const (
	MessageTypeText  MessageType = "TEXT"
	MessageTypeImage MessageType = "IMAGE"
	MessageTypeFile  MessageType = "FILE"
)

type Message struct {
	MessageID   uuid.UUID      `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"messageId"`
	ChatID      uuid.UUID      `gorm:"type:uuid;not null;index:idx_chat_created" json:"chatId"`
	UserID      uuid.UUID      `gorm:"type:uuid;not null;index" json:"userId"`
	Content     string         `gorm:"type:text;not null" json:"content"`
	MessageType MessageType    `gorm:"type:varchar(20);default:'TEXT'" json:"messageType"`
	FileURL     *string        `gorm:"type:text" json:"fileUrl,omitempty"`
	FileName    *string        `gorm:"type:varchar(255)" json:"fileName,omitempty"`
	FileSize    *int64         `json:"fileSize,omitempty"`
	CreatedAt   time.Time      `gorm:"autoCreateTime" json:"createdAt"`
	UpdatedAt   time.Time      `gorm:"autoUpdateTime" json:"updatedAt"`
	DeletedAt   gorm.DeletedAt `gorm:"index" json:"deletedAt,omitempty"`
	Reads       []MessageRead  `gorm:"foreignKey:MessageID" json:"reads,omitempty"`
}

type MessageRead struct {
	ReadID    uuid.UUID `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"readId"`
	MessageID uuid.UUID `gorm:"type:uuid;not null;index:idx_message_user" json:"messageId"`
	UserID    uuid.UUID `gorm:"type:uuid;not null;index:idx_message_user" json:"userId"`
	ReadAt    time.Time `gorm:"autoCreateTime" json:"readAt"`
}

func (Message) TableName() string {
	return "messages"
}

func (MessageRead) TableName() string {
	return "message_reads"
}