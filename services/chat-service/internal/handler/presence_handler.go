// internal/handler/presence_handler.go (새로 생성)
package handler

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

type PresenceHandler struct {
	wsHandler *WSHandler
}

func NewPresenceHandler(wsHandler *WSHandler) *PresenceHandler {
	return &PresenceHandler{
		wsHandler: wsHandler,
	}
}

// GetOnlineUsers godoc
// @Summary      온라인 사용자 목록
// @Description  현재 온라인인 사용자 목록을 가져옵니다
// @Tags         presence
// @Produce      json
// @Success      200 {object} map[string][]string
// @Router       /presence/online [get]
// @Security     BearerAuth
func (h *PresenceHandler) GetOnlineUsers(c *gin.Context) {
	users := h.wsHandler.hub.GetOnlineUsers()
	c.JSON(http.StatusOK, gin.H{
		"onlineUsers": users,
		"count":       len(users),
	})
}

// CheckUserStatus godoc
// @Summary      사용자 온라인 여부 확인
// @Description  특정 사용자가 온라인인지 확인합니다
// @Tags         presence
// @Produce      json
// @Param        userId path string true "User ID"
// @Success      200 {object} map[string]bool
// @Router       /presence/status/{userId} [get]
// @Security     BearerAuth
func (h *PresenceHandler) CheckUserStatus(c *gin.Context) {
	userIDStr := c.Param("userId")
	userID, err := uuid.Parse(userIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid user ID"})
		return
	}

	isOnline := h.wsHandler.hub.IsUserOnline(userID)
	c.JSON(http.StatusOK, gin.H{
		"userId":   userIDStr,
		"isOnline": isOnline,
	})
}