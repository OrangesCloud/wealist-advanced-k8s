package handler

import (
	"chat-service/internal/domain"
	"chat-service/internal/service"
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"go.uber.org/zap"
)

type ChatHandler struct {
	chatService     *service.ChatService
	presenceService *service.PresenceService
	logger          *zap.Logger
}

func NewChatHandler(
	chatService *service.ChatService,
	presenceService *service.PresenceService,
	logger *zap.Logger,
) *ChatHandler {
	return &ChatHandler{
		chatService:     chatService,
		presenceService: presenceService,
		logger:          logger,
	}
}

// CreateChat creates a new chat
func (h *ChatHandler) CreateChat(c *gin.Context) {
	userID := c.MustGet("user_id").(uuid.UUID)

	var req domain.CreateChatRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   gin.H{"code": "BAD_REQUEST", "message": err.Error()},
		})
		return
	}

	chat, err := h.chatService.CreateChat(c.Request.Context(), &req, userID)
	if err != nil {
		h.logger.Error("failed to create chat", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   gin.H{"code": "INTERNAL_ERROR", "message": "Failed to create chat"},
		})
		return
	}

	c.JSON(http.StatusCreated, chat)
}

// GetMyChats returns user's chats
func (h *ChatHandler) GetMyChats(c *gin.Context) {
	userID := c.MustGet("user_id").(uuid.UUID)

	chats, err := h.chatService.GetUserChats(c.Request.Context(), userID)
	if err != nil {
		h.logger.Error("failed to get user chats", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   gin.H{"code": "INTERNAL_ERROR", "message": "Failed to get chats"},
		})
		return
	}

	c.JSON(http.StatusOK, chats)
}

// GetWorkspaceChats returns chats in a workspace
func (h *ChatHandler) GetWorkspaceChats(c *gin.Context) {
	workspaceID, err := uuid.Parse(c.Param("workspaceId"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   gin.H{"code": "BAD_REQUEST", "message": "Invalid workspace ID"},
		})
		return
	}

	chats, err := h.chatService.GetWorkspaceChats(c.Request.Context(), workspaceID)
	if err != nil {
		h.logger.Error("failed to get workspace chats", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   gin.H{"code": "INTERNAL_ERROR", "message": "Failed to get chats"},
		})
		return
	}

	c.JSON(http.StatusOK, chats)
}

// GetChat returns a specific chat
func (h *ChatHandler) GetChat(c *gin.Context) {
	userID := c.MustGet("user_id").(uuid.UUID)

	chatID, err := uuid.Parse(c.Param("chatId"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   gin.H{"code": "BAD_REQUEST", "message": "Invalid chat ID"},
		})
		return
	}

	// Verify user is in chat
	inChat, err := h.chatService.IsUserInChat(c.Request.Context(), chatID, userID)
	if err != nil || !inChat {
		c.JSON(http.StatusForbidden, gin.H{
			"success": false,
			"error":   gin.H{"code": "FORBIDDEN", "message": "Not a participant"},
		})
		return
	}

	chat, err := h.chatService.GetChatByID(c.Request.Context(), chatID)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{
			"success": false,
			"error":   gin.H{"code": "NOT_FOUND", "message": "Chat not found"},
		})
		return
	}

	c.JSON(http.StatusOK, chat)
}

// DeleteChat soft deletes a chat
func (h *ChatHandler) DeleteChat(c *gin.Context) {
	userID := c.MustGet("user_id").(uuid.UUID)

	chatID, err := uuid.Parse(c.Param("chatId"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   gin.H{"code": "BAD_REQUEST", "message": "Invalid chat ID"},
		})
		return
	}

	// Verify user is in chat
	inChat, _ := h.chatService.IsUserInChat(c.Request.Context(), chatID, userID)
	if !inChat {
		c.JSON(http.StatusForbidden, gin.H{
			"success": false,
			"error":   gin.H{"code": "FORBIDDEN", "message": "Not a participant"},
		})
		return
	}

	if err := h.chatService.DeleteChat(c.Request.Context(), chatID); err != nil {
		h.logger.Error("failed to delete chat", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   gin.H{"code": "INTERNAL_ERROR", "message": "Failed to delete chat"},
		})
		return
	}

	c.Status(http.StatusNoContent)
}

// AddParticipants adds participants to a chat
func (h *ChatHandler) AddParticipants(c *gin.Context) {
	userID := c.MustGet("user_id").(uuid.UUID)

	chatID, err := uuid.Parse(c.Param("chatId"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   gin.H{"code": "BAD_REQUEST", "message": "Invalid chat ID"},
		})
		return
	}

	var req struct {
		UserIDs []uuid.UUID `json:"userIds" binding:"required,min=1"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   gin.H{"code": "BAD_REQUEST", "message": err.Error()},
		})
		return
	}

	// Verify user is in chat
	inChat, _ := h.chatService.IsUserInChat(c.Request.Context(), chatID, userID)
	if !inChat {
		c.JSON(http.StatusForbidden, gin.H{
			"success": false,
			"error":   gin.H{"code": "FORBIDDEN", "message": "Not a participant"},
		})
		return
	}

	if err := h.chatService.AddParticipants(c.Request.Context(), chatID, req.UserIDs); err != nil {
		h.logger.Error("failed to add participants", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   gin.H{"code": "INTERNAL_ERROR", "message": "Failed to add participants"},
		})
		return
	}

	chat, _ := h.chatService.GetChatByID(c.Request.Context(), chatID)
	c.JSON(http.StatusOK, chat)
}

// RemoveParticipant removes a participant from a chat
func (h *ChatHandler) RemoveParticipant(c *gin.Context) {
	currentUserID := c.MustGet("user_id").(uuid.UUID)

	chatID, err := uuid.Parse(c.Param("chatId"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   gin.H{"code": "BAD_REQUEST", "message": "Invalid chat ID"},
		})
		return
	}

	userID, err := uuid.Parse(c.Param("userId"))
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"success": false,
			"error":   gin.H{"code": "BAD_REQUEST", "message": "Invalid user ID"},
		})
		return
	}

	// Verify current user is in chat
	inChat, _ := h.chatService.IsUserInChat(c.Request.Context(), chatID, currentUserID)
	if !inChat {
		c.JSON(http.StatusForbidden, gin.H{
			"success": false,
			"error":   gin.H{"code": "FORBIDDEN", "message": "Not a participant"},
		})
		return
	}

	if err := h.chatService.RemoveParticipant(c.Request.Context(), chatID, userID); err != nil {
		h.logger.Error("failed to remove participant", zap.Error(err))
		c.JSON(http.StatusInternalServerError, gin.H{
			"success": false,
			"error":   gin.H{"code": "INTERNAL_ERROR", "message": "Failed to remove participant"},
		})
		return
	}

	c.Status(http.StatusNoContent)
}
