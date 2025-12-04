package repository

import (
	"chat-service/internal/model"
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

type ChatRepository interface {
	CreateChat(chat *model.Chat) error
	GetChatByID(chatID uuid.UUID) (*model.Chat, error)
	GetChatsByWorkspace(workspaceID uuid.UUID) ([]model.Chat, error)
	GetChatsByUser(userID uuid.UUID) ([]model.Chat, error)
	UpdateChat(chat *model.Chat) error
	DeleteChat(chatID uuid.UUID) error
	
	AddParticipant(participant *model.ChatParticipant) error
	RemoveParticipant(chatID, userID uuid.UUID) error
	GetParticipants(chatID uuid.UUID) ([]model.ChatParticipant, error)
	IsParticipant(chatID, userID uuid.UUID) (bool, error)
	UpdateLastRead(chatID, userID uuid.UUID) error
}

type chatRepository struct {
	db *gorm.DB
}

func NewChatRepository(db *gorm.DB) ChatRepository {
	return &chatRepository{db: db}
}

func (r *chatRepository) CreateChat(chat *model.Chat) error {
	return r.db.Create(chat).Error
}

func (r *chatRepository) GetChatByID(chatID uuid.UUID) (*model.Chat, error) {
	var chat model.Chat
	err := r.db.Preload("Participants").
		Preload("Messages", func(db *gorm.DB) *gorm.DB {
			return db.Order("created_at DESC").Limit(50)
		}).
		First(&chat, "chat_id = ?", chatID).Error
	
	if err != nil {
		return nil, err
	}
	return &chat, nil
}

func (r *chatRepository) GetChatsByWorkspace(workspaceID uuid.UUID) ([]model.Chat, error) {
	var chats []model.Chat
	err := r.db.Preload("Participants").
		Where("workspace_id = ?", workspaceID).
		Order("updated_at DESC").
		Find(&chats).Error
	
	return chats, err
}

func (r *chatRepository) GetChatsByUser(userID uuid.UUID) ([]model.Chat, error) {
	var chats []model.Chat
	
	err := r.db.
		Joins("JOIN chat_participants ON chat_participants.chat_id = chats.chat_id").
		Where("chat_participants.user_id = ? AND chat_participants.is_active = true", userID).
		Preload("Participants").
		Order("chats.updated_at DESC").
		Find(&chats).Error
	
	return chats, err
}

func (r *chatRepository) UpdateChat(chat *model.Chat) error {
	return r.db.Save(chat).Error
}

func (r *chatRepository) DeleteChat(chatID uuid.UUID) error {
	return r.db.Delete(&model.Chat{}, "chat_id = ?", chatID).Error
}

func (r *chatRepository) AddParticipant(participant *model.ChatParticipant) error {
	return r.db.Create(participant).Error
}

func (r *chatRepository) RemoveParticipant(chatID, userID uuid.UUID) error {
	return r.db.Model(&model.ChatParticipant{}).
		Where("chat_id = ? AND user_id = ?", chatID, userID).
		Update("is_active", false).Error
}

func (r *chatRepository) GetParticipants(chatID uuid.UUID) ([]model.ChatParticipant, error) {
	var participants []model.ChatParticipant
	err := r.db.Where("chat_id = ? AND is_active = true", chatID).
		Find(&participants).Error
	
	return participants, err
}

func (r *chatRepository) IsParticipant(chatID, userID uuid.UUID) (bool, error) {
	var count int64
	err := r.db.Model(&model.ChatParticipant{}).
		Where("chat_id = ? AND user_id = ? AND is_active = true", chatID, userID).
		Count(&count).Error
	
	return count > 0, err
}

func (r *chatRepository) UpdateLastRead(chatID, userID uuid.UUID) error {
	return r.db.Model(&model.ChatParticipant{}).
		Where("chat_id = ? AND user_id = ?", chatID, userID).
		Update("last_read_at", time.Now()).Error
}