// internal/handler/ws_handler.go
package handler

import (
	"chat-service/internal/client"
	"chat-service/internal/database"
	"chat-service/internal/model"
	"chat-service/internal/service"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/gorilla/websocket"
	"go.uber.org/zap"
)

const (
	writeWait      = 10 * time.Second
	pongWait       = 60 * time.Second
	pingPeriod     = (pongWait * 9) / 10
	maxMessageSize = 8192
)

var upgrader = websocket.Upgrader{
	CheckOrigin: func(r *http.Request) bool { return true },
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
}


type WSMessage struct {
	Type        string                 `json:"type"`
	ChatID      string                 `json:"chatId,omitempty"`
	Content     string                 `json:"content,omitempty"`
	MessageType string                 `json:"messageType,omitempty"`
	FileURL     *string                `json:"fileUrl,omitempty"`
	FileName    *string                `json:"fileName,omitempty"`
	FileSize    *int64                 `json:"fileSize,omitempty"`
	MessageID   string                 `json:"messageId,omitempty"`
	UserID      string                 `json:"userId,omitempty"`
	Timestamp   time.Time              `json:"timestamp,omitempty"`
	Payload     map[string]interface{} `json:"payload,omitempty"`
}

type Client struct {
	conn      *websocket.Conn
	send      chan []byte
	chatID    uuid.UUID
	userID    uuid.UUID
	userName  string // 🔥 사용자 이름 추가
	hub       *Hub
}


type Hub struct {
	clients        map[uuid.UUID]map[*Client]bool
	clientsMu      sync.RWMutex
	register       chan *Client
	unregister     chan *Client
	broadcast      chan []byte
	logger         *zap.Logger

	// 🔥 온라인 상태 추가
	onlineUsers       map[uuid.UUID]bool // userID -> isOnline
	onlineUsersMu     sync.RWMutex
	presenceClients   map[uuid.UUID]int  // userID -> connection count (presence WebSocket)
	presenceClientsMu sync.RWMutex

	// 🔥 Presence WebSocket 연결 저장 (메시지 알림용)
	presenceConns   map[uuid.UUID][]*websocket.Conn // userID -> connections
	presenceConnsMu sync.RWMutex
}

type WSHandler struct {
	logger         *zap.Logger
	userClient     client.UserClient
	messageService service.MessageService
	chatService    service.ChatService
	hub            *Hub
}

func NewWSHandler(
	logger *zap.Logger,
	userClient client.UserClient,
	messageService service.MessageService,
	chatService service.ChatService,
) *WSHandler {
	hub := &Hub{
		clients:         make(map[uuid.UUID]map[*Client]bool),
		register:        make(chan *Client),
		unregister:      make(chan *Client),
		broadcast:       make(chan []byte, 256),
		logger:          logger,
		onlineUsers:     make(map[uuid.UUID]bool),              // 🔥 초기화
		presenceClients: make(map[uuid.UUID]int),               // 🔥 Presence 클라이언트 추적
		presenceConns:   make(map[uuid.UUID][]*websocket.Conn), // 🔥 Presence WebSocket 연결 저장
	}

	go hub.run()

	return &WSHandler{
		logger:         logger,
		userClient:     userClient,
		messageService: messageService,
		chatService:    chatService,
		hub:            hub,
	}
}

func (h *Hub) run() {
	for {
		select {
		case client := <-h.register:
			h.clientsMu.Lock()
			if h.clients[client.chatID] == nil {
				h.clients[client.chatID] = make(map[*Client]bool)
			}
			h.clients[client.chatID][client] = true
			h.clientsMu.Unlock()
			
			// 🔥 온라인 상태 업데이트
			h.onlineUsersMu.Lock()
			h.onlineUsers[client.userID] = true
			h.onlineUsersMu.Unlock()
			
			h.logger.Info("Client registered",
				zap.String("chatId", client.chatID.String()),
				zap.String("userId", client.userID.String()))
			
			// 🔥 온라인 알림 브로드캐스트
			h.broadcastUserStatus(client.userID, true)

		case client := <-h.unregister:
			h.clientsMu.Lock()
			if clients, ok := h.clients[client.chatID]; ok {
				if _, exists := clients[client]; exists {
					delete(clients, client)
					close(client.send)
					if len(clients) == 0 {
						delete(h.clients, client.chatID)
					}
				}
			}
			h.clientsMu.Unlock()

			// 🔥 온라인 상태 확인 (다른 채팅방 또는 Presence에 연결되어 있는지 확인)
			isStillOnline := false

			// 1. Presence WebSocket 확인
			h.presenceClientsMu.RLock()
			if h.presenceClients[client.userID] > 0 {
				isStillOnline = true
			}
			h.presenceClientsMu.RUnlock()

			// 2. 다른 채팅방 WebSocket 확인
			if !isStillOnline {
				h.clientsMu.RLock()
				for _, chatClients := range h.clients {
					for c := range chatClients {
						if c.userID == client.userID {
							isStillOnline = true
							break
						}
					}
					if isStillOnline {
						break
					}
				}
				h.clientsMu.RUnlock()
			}

			if !isStillOnline {
				h.onlineUsersMu.Lock()
				delete(h.onlineUsers, client.userID)
				h.onlineUsersMu.Unlock()

				// 🔥 오프라인 알림 브로드캐스트
				h.broadcastUserStatus(client.userID, false)
			}

			h.logger.Info("Client unregistered",
				zap.String("chatId", client.chatID.String()),
				zap.String("userId", client.userID.String()),
				zap.Bool("stillOnline", isStillOnline))
		}
	}
}

// 🔥 사용자 온라인 상태 브로드캐스트
func (h *Hub) broadcastUserStatus(userID uuid.UUID, isOnline bool) {
	status := "OFFLINE"
	if isOnline {
		status = "ONLINE"
	}
	
	payload, _ := json.Marshal(WSMessage{
		Type:   "USER_STATUS",
		UserID: userID.String(),
		Payload: map[string]interface{}{
			"status": status,
		},
	})
	
	// 모든 채팅방에 브로드캐스트
	h.clientsMu.RLock()
	defer h.clientsMu.RUnlock()
	
	for _, chatClients := range h.clients {
		for client := range chatClients {
			select {
			case client.send <- payload:
			default:
			}
		}
	}
}

// 🔥 온라인 사용자 목록 가져오기 (API용)
func (h *Hub) GetOnlineUsers() []string {
	h.onlineUsersMu.RLock()
	defer h.onlineUsersMu.RUnlock()
	
	users := make([]string, 0, len(h.onlineUsers))
	for userID := range h.onlineUsers {
		users = append(users, userID.String())
	}
	return users
}

// 🔥 특정 사용자 온라인 여부 확인 (API용)
func (h *Hub) IsUserOnline(userID uuid.UUID) bool {
	h.onlineUsersMu.RLock()
	defer h.onlineUsersMu.RUnlock()
	return h.onlineUsers[userID]
}

// 🔥 Presence WebSocket으로 메시지 알림 전송
func (h *Hub) SendNotificationToUser(userID uuid.UUID, notification []byte) {
	h.presenceConnsMu.RLock()
	conns := h.presenceConns[userID]
	h.presenceConnsMu.RUnlock()

	for _, conn := range conns {
		if conn != nil {
			conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := conn.WriteMessage(websocket.TextMessage, notification); err != nil {
				h.logger.Warn("Failed to send notification to user",
					zap.String("userId", userID.String()),
					zap.Error(err))
			}
		}
	}
}

// 🔥 Presence 연결 추가
func (h *Hub) AddPresenceConn(userID uuid.UUID, conn *websocket.Conn) {
	h.presenceConnsMu.Lock()
	h.presenceConns[userID] = append(h.presenceConns[userID], conn)
	h.presenceConnsMu.Unlock()
}

// 🔥 Presence 연결 제거
func (h *Hub) RemovePresenceConn(userID uuid.UUID, conn *websocket.Conn) {
	h.presenceConnsMu.Lock()
	defer h.presenceConnsMu.Unlock()

	conns := h.presenceConns[userID]
	for i, c := range conns {
		if c == conn {
			h.presenceConns[userID] = append(conns[:i], conns[i+1:]...)
			break
		}
	}
	if len(h.presenceConns[userID]) == 0 {
		delete(h.presenceConns, userID)
	}
}

func (h *Hub) broadcastToChat(chatID uuid.UUID, message []byte) {
	h.clientsMu.RLock()
	clients := h.clients[chatID]
	h.clientsMu.RUnlock()

	for client := range clients {
		select {
		case client.send <- message:
		default:
			close(client.send)
			h.unregister <- client
		}
	}
}

// HandleWebSocket godoc
// @Summary      WebSocket 연결
// @Description  채팅방 WebSocket에 연결합니다
// @Tags         websocket
// @Param        chatId path string true "Chat ID"
// @Param        token query string true "JWT Access Token"
// @Success      101 {string} string "Switching Protocols"
// @Failure      401 {object} map[string]string
// @Router       /ws/chat/{chatId} [get]
func (h *WSHandler) HandleWebSocket(c *gin.Context) {
	chatIDStr := c.Param("chatId")
	chatID, err := uuid.Parse(chatIDStr)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid chat ID"})
		return
	}

	// 토큰 검증
	token := c.Query("token")
	if token == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token required"})
		return
	}

	ctx, cancel := context.WithTimeout(c.Request.Context(), 5*time.Second)
	defer cancel()

	validationResp, err := h.userClient.ValidateToken(ctx, token)
	if err != nil || !validationResp.Valid {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Invalid token"})
		return
	}

	userID, err := uuid.Parse(validationResp.UserID)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid user ID"})
		return
	}

	// 참여자인지 확인
	isParticipant, err := h.chatService.IsParticipant(chatID, userID)
	if err != nil || !isParticipant {
		c.JSON(http.StatusForbidden, gin.H{"error": "Not a participant"})
		return
	}

	// 🔥 사용자 정보 조회 (userName 얻기)
	userName := ""
	userInfo, err := h.userClient.GetUserInfo(ctx, validationResp.UserID, token)
	if err != nil {
		h.logger.Warn("Failed to get user info", zap.Error(err))
		userName = "Unknown" // 실패 시 기본값
	} else {
		userName = userInfo.NickName
	}

	// WebSocket 업그레이드
	conn, err := upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		h.logger.Error("Failed to upgrade connection", zap.Error(err))
		return
	}

	client := &Client{
		conn:     conn,
		send:     make(chan []byte, 256),
		chatID:   chatID,
		userID:   userID,
		userName: userName, // 🔥 사용자 이름 저장
		hub:      h.hub,
	}

	h.hub.register <- client

	// Redis 구독 시작
	go h.subscribeToRedis(client)

	// Goroutines 시작
	go h.writePump(client)
	go h.readPump(client)
}

func (h *WSHandler) readPump(client *Client) {
	defer func() {
		h.hub.unregister <- client
		client.conn.Close()
	}()

	client.conn.SetReadLimit(maxMessageSize)
	client.conn.SetReadDeadline(time.Now().Add(pongWait))
	client.conn.SetPongHandler(func(string) error {
		client.conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	for {
		_, message, err := client.conn.ReadMessage()
		if err != nil {
			if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseAbnormalClosure) {
				h.logger.Error("WebSocket error", zap.Error(err))
			}
			break
		}

		// 메시지 파싱
		var wsMsg WSMessage
		if err := json.Unmarshal(message, &wsMsg); err != nil {
			h.logger.Warn("Failed to parse message", zap.Error(err))
			continue
		}

		// 메시지 타입별 처리
		if err := h.handleMessage(client, &wsMsg); err != nil {
			h.logger.Error("Failed to handle message", zap.Error(err))
		}
	}
}

func (h *WSHandler) writePump(client *Client) {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		client.conn.Close()
	}()

	for {
		select {
		case message, ok := <-client.send:
			client.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if !ok {
				client.conn.WriteMessage(websocket.CloseMessage, []byte{})
				return
			}

			if err := client.conn.WriteMessage(websocket.TextMessage, message); err != nil {
				return
			}

		case <-ticker.C:
			client.conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := client.conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

func (h *WSHandler) handleMessage(client *Client, wsMsg *WSMessage) error {
	switch wsMsg.Type {
	case "MESSAGE":
		return h.handleNewMessage(client, wsMsg)
	case "TYPING_START":
		return h.handleTyping(client, true)
	case "TYPING_STOP":
		return h.handleTyping(client, false)
	case "READ_MESSAGE":
		return h.handleReadMessage(client, wsMsg)
	default:
		h.logger.Warn("Unknown message type", zap.String("type", wsMsg.Type))
	}
	return nil
}

func (h *WSHandler) handleNewMessage(client *Client, wsMsg *WSMessage) error {
	messageType := model.MessageTypeText
	if wsMsg.MessageType != "" {
		messageType = model.MessageType(wsMsg.MessageType)
	}

	message, err := h.messageService.CreateMessage(
		client.chatID,
		client.userID,
		client.userName, // 🔥 사용자 이름 전달
		wsMsg.Content,
		messageType,
		wsMsg.FileURL,
		wsMsg.FileName,
		wsMsg.FileSize,
	)
	if err != nil {
		return err
	}

	// 브로드캐스트는 CreateMessage 내부에서 처리됨
	h.logger.Info("Message created via WebSocket",
		zap.String("messageId", message.MessageID.String()),
		zap.String("chatId", client.chatID.String()),
		zap.String("userName", client.userName))

	// 🔥 Presence WebSocket으로 새 메시지 알림 전송 (발신자 제외)
	go h.notifyParticipantsOfNewMessage(client.chatID, client.userID, message)

	return nil
}

// 🔥 채팅 참여자들에게 새 메시지 알림 (Presence WebSocket)
func (h *WSHandler) notifyParticipantsOfNewMessage(chatID, senderID uuid.UUID, message *model.Message) {
	// 채팅방 참여자 목록 조회
	participants, err := h.chatService.GetParticipants(chatID)
	if err != nil {
		h.logger.Warn("Failed to get chat participants for notification",
			zap.String("chatId", chatID.String()),
			zap.Error(err))
		return
	}

	// 알림 페이로드 생성
	notification, _ := json.Marshal(WSMessage{
		Type:   "NEW_MESSAGE_NOTIFICATION",
		ChatID: chatID.String(),
		Payload: map[string]interface{}{
			"chatId":    chatID.String(),
			"messageId": message.MessageID.String(),
			"senderId":  senderID.String(),
		},
	})

	// 각 참여자에게 알림 전송 (발신자 제외)
	for _, participant := range participants {
		if participant.UserID == senderID {
			continue
		}

		h.hub.SendNotificationToUser(participant.UserID, notification)
		h.logger.Debug("Sent new message notification",
			zap.String("userId", participant.UserID.String()),
			zap.String("chatId", chatID.String()))
	}
}

func (h *WSHandler) handleTyping(client *Client, isTyping bool) error {
	eventType := "USER_TYPING_STOP"
	if isTyping {
		eventType = "USER_TYPING"
	}

	payload, _ := json.Marshal(WSMessage{
		Type:   eventType,
		ChatID: client.chatID.String(),
		UserID: client.userID.String(),
	})

	h.hub.broadcastToChat(client.chatID, payload)
	return nil
}

func (h *WSHandler) handleReadMessage(client *Client, wsMsg *WSMessage) error {
	if wsMsg.MessageID == "" {
		return fmt.Errorf("messageId required")
	}

	messageID, err := uuid.Parse(wsMsg.MessageID)
	if err != nil {
		return err
	}

	if err := h.messageService.MarkAsRead(messageID, client.userID); err != nil {
		return err
	}

	// 읽음 알림 브로드캐스트
	payload, _ := json.Marshal(WSMessage{
		Type:      "MESSAGE_READ",
		MessageID: wsMsg.MessageID,
		UserID:    client.userID.String(),
		ChatID:    client.chatID.String(),
		Timestamp: time.Now(),
	})

	h.hub.broadcastToChat(client.chatID, payload)
	return nil
}

// 🔥 Global Presence WebSocket - 앱 접속 시 온라인 상태 등록
// HandlePresenceWebSocket godoc
// @Summary      Global Presence WebSocket 연결
// @Description  앱 접속 시 온라인 상태를 등록합니다 (채팅방 없이)
// @Tags         websocket
// @Param        token query string true "JWT Access Token"
// @Success      101 {string} string "Switching Protocols"
// @Failure      401 {object} map[string]string
// @Router       /ws/presence [get]
func (h *WSHandler) HandlePresenceWebSocket(c *gin.Context) {
	// 토큰 검증
	token := c.Query("token")
	if token == "" {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Token required"})
		return
	}

	ctx, cancel := context.WithTimeout(c.Request.Context(), 5*time.Second)
	defer cancel()

	validationResp, err := h.userClient.ValidateToken(ctx, token)
	if err != nil || !validationResp.Valid {
		h.logger.Warn("Invalid token for presence", zap.Error(err))
		c.JSON(http.StatusUnauthorized, gin.H{"error": "Invalid token"})
		return
	}

	userID, err := uuid.Parse(validationResp.UserID)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "Invalid user ID"})
		return
	}

	// WebSocket 업그레이드
	conn, err := upgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		h.logger.Error("Failed to upgrade presence connection", zap.Error(err))
		return
	}

	h.logger.Info("Presence WebSocket connected",
		zap.String("userId", userID.String()))

	// 🔥 Presence 클라이언트 등록
	h.hub.presenceClientsMu.Lock()
	h.hub.presenceClients[userID]++
	h.hub.presenceClientsMu.Unlock()

	// 🔥 Presence 연결 저장 (메시지 알림용)
	h.hub.AddPresenceConn(userID, conn)

	// 온라인 상태 등록
	h.hub.onlineUsersMu.Lock()
	h.hub.onlineUsers[userID] = true
	h.hub.onlineUsersMu.Unlock()

	// 온라인 알림 브로드캐스트
	h.hub.broadcastUserStatus(userID, true)

	// Ping-Pong으로 연결 유지
	go h.presenceWritePump(conn, userID)
	h.presenceReadPump(conn, userID)
}

func (h *WSHandler) presenceReadPump(conn *websocket.Conn, userID uuid.UUID) {
	defer func() {
		conn.Close()

		// 🔥 Presence 연결 제거 (메시지 알림용)
		h.hub.RemovePresenceConn(userID, conn)

		// 🔥 Presence 클라이언트 감소
		h.hub.presenceClientsMu.Lock()
		h.hub.presenceClients[userID]--
		presenceCount := h.hub.presenceClients[userID]
		if presenceCount <= 0 {
			delete(h.hub.presenceClients, userID)
		}
		h.hub.presenceClientsMu.Unlock()

		// 🔥 다른 연결이 있는지 확인 (채팅방 + Presence)
		isStillOnline := false

		// 1. Presence WebSocket 확인
		if presenceCount > 0 {
			isStillOnline = true
		}

		// 2. 채팅방 WebSocket 확인
		if !isStillOnline {
			h.hub.clientsMu.RLock()
			for _, chatClients := range h.hub.clients {
				for c := range chatClients {
					if c.userID == userID {
						isStillOnline = true
						break
					}
				}
				if isStillOnline {
					break
				}
			}
			h.hub.clientsMu.RUnlock()
		}

		if !isStillOnline {
			h.hub.onlineUsersMu.Lock()
			delete(h.hub.onlineUsers, userID)
			h.hub.onlineUsersMu.Unlock()

			h.hub.broadcastUserStatus(userID, false)
		}

		h.logger.Info("Presence WebSocket disconnected",
			zap.String("userId", userID.String()),
			zap.Bool("stillOnline", isStillOnline))
	}()

	conn.SetReadLimit(maxMessageSize)
	conn.SetReadDeadline(time.Now().Add(pongWait))
	conn.SetPongHandler(func(string) error {
		conn.SetReadDeadline(time.Now().Add(pongWait))
		return nil
	})

	for {
		_, _, err := conn.ReadMessage()
		if err != nil {
			break
		}
	}
}

func (h *WSHandler) presenceWritePump(conn *websocket.Conn, userID uuid.UUID) {
	ticker := time.NewTicker(pingPeriod)
	defer func() {
		ticker.Stop()
		conn.Close()
	}()

	for {
		select {
		case <-ticker.C:
			conn.SetWriteDeadline(time.Now().Add(writeWait))
			if err := conn.WriteMessage(websocket.PingMessage, nil); err != nil {
				return
			}
		}
	}
}

func (h *WSHandler) subscribeToRedis(client *Client) {
	defer func() {
		if r := recover(); r != nil {
			h.logger.Error("Recovered from panic in subscribeToRedis",
				zap.Any("panic", r),
				zap.String("chatId", client.chatID.String()))
		}
	}()

	pubsub := database.SubscribeChatEvents(client.chatID.String())
	if pubsub == nil {
		h.logger.Warn("Redis pubsub not available")
		return
	}
	defer pubsub.Close()

	ch := pubsub.Channel()
	for msg := range ch {
		select {
		case client.send <- []byte(msg.Payload):
		case <-time.After(1 * time.Second):
			h.logger.Warn("Failed to send Redis message to client",
				zap.String("chatId", client.chatID.String()))
			return
		}
	}
}