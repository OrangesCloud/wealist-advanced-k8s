// internal/repository/message_repository.go
package repository

import (
	"chat-service/internal/model"
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

type MessageRepository interface {
	CreateMessage(message *model.Message) error
	GetMessageByID(messageID uuid.UUID) (*model.Message, error)
	GetMessagesByChatID(chatID uuid.UUID, limit, offset int) ([]model.Message, error)
	GetMessagesAfter(chatID uuid.UUID, after time.Time, limit int) ([]model.Message, error)
	UpdateMessage(message *model.Message) error
	DeleteMessage(messageID uuid.UUID) error
	
	MarkAsRead(messageID, userID uuid.UUID) error
	GetUnreadCount(chatID, userID uuid.UUID) (int64, error)
	GetMessageReads(messageID uuid.UUID) ([]model.MessageRead, error)
}

type messageRepository struct {
	db *gorm.DB
}

func NewMessageRepository(db *gorm.DB) MessageRepository {
	return &messageRepository{db: db}
}

func (r *messageRepository) CreateMessage(message *model.Message) error {
	return r.db.Create(message).Error
}

func (r *messageRepository) GetMessageByID(messageID uuid.UUID) (*model.Message, error) {
	var message model.Message
	err := r.db.Preload("Reads").
		First(&message, "message_id = ?", messageID).Error
	
	if err != nil {
		return nil, err
	}
	return &message, nil
}

func (r *messageRepository) GetMessagesByChatID(chatID uuid.UUID, limit, offset int) ([]model.Message, error) {
	var messages []model.Message
	err := r.db.Where("chat_id = ?", chatID).
		Order("created_at ASC").
		Limit(limit).
		Offset(offset).
		Preload("Reads").
		Find(&messages).Error

	return messages, err
}

func (r *messageRepository) GetMessagesAfter(chatID uuid.UUID, after time.Time, limit int) ([]model.Message, error) {
	var messages []model.Message
	err := r.db.Where("chat_id = ? AND created_at > ?", chatID, after).
		Order("created_at ASC").
		Limit(limit).
		Preload("Reads").
		Find(&messages).Error
	
	return messages, err
}

func (r *messageRepository) UpdateMessage(message *model.Message) error {
	return r.db.Save(message).Error
}

func (r *messageRepository) DeleteMessage(messageID uuid.UUID) error {
	return r.db.Delete(&model.Message{}, "message_id = ?", messageID).Error
}

func (r *messageRepository) MarkAsRead(messageID, userID uuid.UUID) error {
	read := &model.MessageRead{
		MessageID: messageID,
		UserID:    userID,
	}
	return r.db.Create(read).Error
}

func (r *messageRepository) GetUnreadCount(chatID, userID uuid.UUID) (int64, error) {
	// 사용자의 마지막 읽은 시간 가져오기
	var participant model.ChatParticipant
	err := r.db.Where("chat_id = ? AND user_id = ?", chatID, userID).
		First(&participant).Error
	if err != nil {
		return 0, err
	}

	// 마지막 읽은 시간 이후 메시지 개수 (🔥 본인이 보낸 메시지 제외!)
	var count int64
	err = r.db.Model(&model.Message{}).
		Where("chat_id = ? AND created_at > ? AND user_id != ?", chatID, participant.LastReadAt, userID).
		Count(&count).Error

	return count, err
}

func (r *messageRepository) GetMessageReads(messageID uuid.UUID) ([]model.MessageRead, error) {
	var reads []model.MessageRead
	err := r.db.Where("message_id = ?", messageID).
		Find(&reads).Error
	
	return reads, err
}