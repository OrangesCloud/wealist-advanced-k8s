package database

import (
	"context"
	"project-board-api/internal/config"

	"github.com/go-redis/redis/v8"
	"go.uber.org/zap"
)

var RedisClient *redis.Client

func InitRedis(cfg config.Config, log *zap.Logger) error {
	var client *redis.Client

	// redis:// 형식 URL 있으면 우선 사용
	if cfg.Redis.URL != "" {
		opts, err := redis.ParseURL(cfg.Redis.URL)
		if err != nil {
			return err
		}
		client = redis.NewClient(opts)
	} else {
		client = redis.NewClient(&redis.Options{
			Addr:     "redis:6379",       // docker-compose 내 컨테이너 이름
			Password: cfg.Redis.Password, // ← 이거 제대로 전달됨!
			DB:       1,
		})
	}

	// 연결 테스트
	if err := client.Ping(context.Background()).Err(); err != nil {
		return err
	}

	RedisClient = client
	log.Info("Redis connection established successfully", zap.String("addr", "redis:6379"), zap.Int("db", cfg.Redis.DB))
	return nil
}

func GetRedis() *redis.Client {
	// Return nil instead of panicking to allow tests to run without Redis
	return RedisClient
}
