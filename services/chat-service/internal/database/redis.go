// internal/database/redis.go
package database

import (
	"context"
	"fmt"
	"log"
	"os"
	"strings"

	"github.com/go-redis/redis/v8"
)

var redisClient *redis.Client

// InitRedis initializes Redis connection
func InitRedis() *redis.Client {
	redisURL := os.Getenv("REDIS_URL")
	if redisURL == "" {
		log.Println("⚠️ REDIS_URL not set, using default: redis://:password@localhost:6379/2")
		redisURL = "redis://:password@localhost:6379/2"
	}

	// Parse Redis URL
	opt, err := redis.ParseURL(redisURL)
	if err != nil {
		log.Printf("❌ Failed to parse REDIS_URL: %v", err)
		return nil
	}

	redisClient = redis.NewClient(opt)

	// Test connection
	ctx := context.Background()
	if err := redisClient.Ping(ctx).Err(); err != nil {
		log.Printf("❌ Failed to connect to Redis: %v", err)
		return nil
	}

	log.Println("✅ Redis connected successfully")
	return redisClient
}

// GetRedis returns the Redis client
func GetRedis() *redis.Client {
	return redisClient
}

// PublishChatEvent publishes an event to Redis
func PublishChatEvent(chatID string, payload []byte) error {
	if redisClient == nil {
		return fmt.Errorf("redis client not initialized")
	}

	channel := fmt.Sprintf("chat:%s", chatID)
	ctx := context.Background()
	
	return redisClient.Publish(ctx, channel, payload).Err()
}

// SubscribeChatEvents subscribes to chat events
func SubscribeChatEvents(chatID string) *redis.PubSub {
	if redisClient == nil {
		return nil
	}

	channel := fmt.Sprintf("chat:%s", chatID)
	ctx := context.Background()
	
	return redisClient.Subscribe(ctx, channel)
}

// SetUserOnline sets user online status in Redis
func SetUserOnline(userID, workspaceID string) error {
	if redisClient == nil {
		return fmt.Errorf("redis client not initialized")
	}

	ctx := context.Background()
	key := fmt.Sprintf("presence:%s:%s", workspaceID, userID)
	
	return redisClient.Set(ctx, key, "ONLINE", 0).Err()
}

// SetUserOffline sets user offline status
func SetUserOffline(userID, workspaceID string) error {
	if redisClient == nil {
		return fmt.Errorf("redis client not initialized")
	}

	ctx := context.Background()
	key := fmt.Sprintf("presence:%s:%s", workspaceID, userID)
	
	return redisClient.Del(ctx, key).Err()
}

// GetOnlineUsers gets all online users in workspace
func GetOnlineUsers(workspaceID string) ([]string, error) {
	if redisClient == nil {
		return nil, fmt.Errorf("redis client not initialized")
	}

	ctx := context.Background()
	pattern := fmt.Sprintf("presence:%s:*", workspaceID)
	
	keys, err := redisClient.Keys(ctx, pattern).Result()
	if err != nil {
		return nil, err
	}

	userIDs := make([]string, 0, len(keys))
	for _, key := range keys {
		parts := strings.Split(key, ":")
		if len(parts) == 3 {
			userIDs = append(userIDs, parts[2])
		}
	}

	return userIDs, nil
}