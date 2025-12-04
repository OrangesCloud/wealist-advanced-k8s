// internal/handler/dto.go
package handler

import (
	"chat-service/internal/model"
	"time"
)

// ========================================
// Chat DTOs
// ========================================

// ChatResponse는 Chat API 응답 DTO입니다
type ChatResponse struct {
	ChatID      string    `json:"chatId" example:"550e8400-e29b-41d4-a716-446655440000"`
	WorkspaceID string    `json:"workspaceId" example:"550e8400-e29b-41d4-a716-446655440001"`
	ProjectID   *string   `json:"projectId,omitempty" example:"550e8400-e29b-41d4-a716-446655440002"`
	ChatType    string    `json:"chatType" example:"PROJECT" enums:"DM,GROUP,PROJECT"`
	ChatName    string    `json:"chatName,omitempty" example:"프로젝트 A 팀 채팅"`
	CreatedBy   string    `json:"createdBy" example:"550e8400-e29b-41d4-a716-446655440003"`
	CreatedAt   time.Time `json:"createdAt" example:"2025-11-20T12:00:00Z"`
	UpdatedAt   time.Time `json:"updatedAt" example:"2025-11-20T12:00:00Z"`
} // @name ChatResponse

// ToChatResponse converts model.Chat to ChatResponse
func ToChatResponse(chat *model.Chat) ChatResponse {
	resp := ChatResponse{
		ChatID:      chat.ChatID.String(),
		WorkspaceID: chat.WorkspaceID.String(),
		ChatType:    string(chat.ChatType),
		ChatName:    chat.ChatName,
		CreatedBy:   chat.CreatedBy.String(),
		CreatedAt:   chat.CreatedAt,
		UpdatedAt:   chat.UpdatedAt,
	}

	if chat.ProjectID != nil {
		projectID := chat.ProjectID.String()
		resp.ProjectID = &projectID
	}

	return resp
}

// ToChatResponses converts []model.Chat to []ChatResponse
func ToChatResponses(chats []model.Chat) []ChatResponse {
	responses := make([]ChatResponse, len(chats))
	for i, chat := range chats {
		responses[i] = ToChatResponse(&chat)
	}
	return responses
}

// ========================================
// Message DTOs
// ========================================

// MessageResponse는 Message API 응답 DTO입니다
type MessageResponse struct {
	MessageID   string    `json:"messageId" example:"550e8400-e29b-41d4-a716-446655440000"`
	ChatID      string    `json:"chatId" example:"550e8400-e29b-41d4-a716-446655440001"`
	UserID      string    `json:"userId" example:"550e8400-e29b-41d4-a716-446655440002"`
	Content     string    `json:"content" example:"안녕하세요!"`
	MessageType string    `json:"messageType" example:"TEXT" enums:"TEXT,IMAGE,FILE"`
	FileURL     *string   `json:"fileUrl,omitempty" example:"https://example.com/file.pdf"`
	FileName    *string   `json:"fileName,omitempty" example:"document.pdf"`
	FileSize    *int64    `json:"fileSize,omitempty" example:"1024000"`
	CreatedAt   time.Time `json:"createdAt" example:"2025-11-20T12:00:00Z"`
	UpdatedAt   time.Time `json:"updatedAt" example:"2025-11-20T12:00:00Z"`
} // @name MessageResponse

// ToMessageResponse converts model.Message to MessageResponse
func ToMessageResponse(msg *model.Message) MessageResponse {
	return MessageResponse{
		MessageID:   msg.MessageID.String(),
		ChatID:      msg.ChatID.String(),
		UserID:      msg.UserID.String(),
		Content:     msg.Content,
		MessageType: string(msg.MessageType),
		FileURL:     msg.FileURL,
		FileName:    msg.FileName,
		FileSize:    msg.FileSize,
		CreatedAt:   msg.CreatedAt,
		UpdatedAt:   msg.UpdatedAt,
	}
}

// ToMessageResponses converts []model.Message to []MessageResponse
func ToMessageResponses(messages []model.Message) []MessageResponse {
	responses := make([]MessageResponse, len(messages))
	for i, msg := range messages {
		responses[i] = ToMessageResponse(&msg)
	}
	return responses
}