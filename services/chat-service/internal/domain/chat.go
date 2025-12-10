package domain

import (
	"time"

	"github.com/google/uuid"
)

// ChatType defines the type of chat
type ChatType string

const (
	ChatTypeDM      ChatType = "DM"
	ChatTypeGroup   ChatType = "GROUP"
	ChatTypeProject ChatType = "PROJECT"
)

// MessageType defines the type of message
type MessageType string

const (
	MessageTypeText  MessageType = "TEXT"
	MessageTypeImage MessageType = "IMAGE"
	MessageTypeFile  MessageType = "FILE"
)

// PresenceStatus defines user presence status
type PresenceStatus string

const (
	PresenceStatusOnline  PresenceStatus = "ONLINE"
	PresenceStatusAway    PresenceStatus = "AWAY"
	PresenceStatusOffline PresenceStatus = "OFFLINE"
)

// Chat represents a chat room
type Chat struct {
	ID           uuid.UUID          `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"chatId"`
	WorkspaceID  uuid.UUID          `gorm:"type:uuid;not null;index" json:"workspaceId"`
	ProjectID    *uuid.UUID         `gorm:"type:uuid;index" json:"projectId,omitempty"`
	ChatType     ChatType           `gorm:"type:varchar(20);not null" json:"chatType"`
	ChatName     string             `gorm:"type:varchar(100);not null" json:"chatName"`
	CreatedBy    uuid.UUID          `gorm:"type:uuid;not null" json:"createdBy"`
	CreatedAt    time.Time          `gorm:"type:timestamptz;default:now();not null" json:"createdAt"`
	UpdatedAt    time.Time          `gorm:"type:timestamptz;default:now();not null" json:"updatedAt"`
	DeletedAt    *time.Time         `gorm:"type:timestamptz;index" json:"deletedAt,omitempty"`
	Participants []ChatParticipant  `gorm:"foreignKey:ChatID" json:"participants,omitempty"`
	Messages     []Message          `gorm:"foreignKey:ChatID" json:"messages,omitempty"`
}

func (Chat) TableName() string {
	return "chats"
}

// ChatParticipant represents a user in a chat
type ChatParticipant struct {
	ID         uuid.UUID  `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"participantId"`
	ChatID     uuid.UUID  `gorm:"type:uuid;not null;index" json:"chatId"`
	UserID     uuid.UUID  `gorm:"type:uuid;not null;index" json:"userId"`
	JoinedAt   time.Time  `gorm:"type:timestamptz;default:now();not null" json:"joinedAt"`
	LastReadAt *time.Time `gorm:"type:timestamptz" json:"lastReadAt,omitempty"`
	IsActive   bool       `gorm:"default:true" json:"isActive"`
}

func (ChatParticipant) TableName() string {
	return "chat_participants"
}

// Message represents a chat message
type Message struct {
	ID          uuid.UUID     `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"messageId"`
	ChatID      uuid.UUID     `gorm:"type:uuid;not null;index:idx_message_chat_created" json:"chatId"`
	UserID      uuid.UUID     `gorm:"type:uuid;not null;index" json:"userId"`
	Content     string        `gorm:"type:text;not null" json:"content"`
	MessageType MessageType   `gorm:"type:varchar(20);default:'TEXT'" json:"messageType"`
	FileURL     *string       `gorm:"type:text" json:"fileUrl,omitempty"`
	FileName    *string       `gorm:"type:varchar(255)" json:"fileName,omitempty"`
	FileSize    *int64        `gorm:"type:bigint" json:"fileSize,omitempty"`
	CreatedAt   time.Time     `gorm:"type:timestamptz;default:now();not null;index:idx_message_chat_created" json:"createdAt"`
	UpdatedAt   time.Time     `gorm:"type:timestamptz;default:now();not null" json:"updatedAt"`
	DeletedAt   *time.Time    `gorm:"type:timestamptz;index" json:"deletedAt,omitempty"`
	Reads       []MessageRead `gorm:"foreignKey:MessageID" json:"reads,omitempty"`
}

func (Message) TableName() string {
	return "messages"
}

// MessageRead represents message read status
type MessageRead struct {
	ID        uuid.UUID `gorm:"type:uuid;primaryKey;default:gen_random_uuid()" json:"readId"`
	MessageID uuid.UUID `gorm:"type:uuid;not null;index;uniqueIndex:idx_message_user_read" json:"messageId"`
	UserID    uuid.UUID `gorm:"type:uuid;not null;index;uniqueIndex:idx_message_user_read" json:"userId"`
	ReadAt    time.Time `gorm:"type:timestamptz;default:now();not null" json:"readAt"`
}

func (MessageRead) TableName() string {
	return "message_reads"
}

// UserPresence represents user online status
type UserPresence struct {
	UserID      uuid.UUID      `gorm:"type:uuid;primaryKey" json:"userId"`
	WorkspaceID uuid.UUID      `gorm:"type:uuid;not null;index:idx_presence_workspace_status" json:"workspaceId"`
	Status      PresenceStatus `gorm:"type:varchar(20);default:'OFFLINE';index:idx_presence_workspace_status" json:"status"`
	LastSeen    time.Time      `gorm:"type:timestamptz;default:now();not null" json:"lastSeen"`
}

func (UserPresence) TableName() string {
	return "user_presences"
}

// CreateChatRequest represents chat creation request
type CreateChatRequest struct {
	WorkspaceID  uuid.UUID   `json:"workspaceId" binding:"required"`
	ProjectID    *uuid.UUID  `json:"projectId,omitempty"`
	ChatType     ChatType    `json:"chatType" binding:"required"`
	ChatName     string      `json:"chatName" binding:"required,max=100"`
	Participants []uuid.UUID `json:"participants" binding:"required,min=1"`
}

// SendMessageRequest represents message sending request
type SendMessageRequest struct {
	Content     string      `json:"content" binding:"required"`
	MessageType MessageType `json:"messageType,omitempty"`
	FileURL     *string     `json:"fileUrl,omitempty"`
	FileName    *string     `json:"fileName,omitempty"`
	FileSize    *int64      `json:"fileSize,omitempty"`
}

// ChatWithUnread represents chat with unread count
type ChatWithUnread struct {
	Chat
	UnreadCount int64 `json:"unreadCount"`
}
