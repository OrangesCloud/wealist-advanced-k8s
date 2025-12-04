// internal/database/postgres.go
package database

import (
	"context"
	"fmt"
	"log"
	"os"
	"sync"
	"time"

	"chat-service/internal/model"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

var (
	db    *gorm.DB
	dbMux sync.RWMutex

	// DBReady는 DB 연결 상태를 나타냄
	DBReady = false
)

// InitPostgres initializes PostgreSQL connection
// DB 연결 실패해도 에러만 반환하고 앱은 계속 실행됨 (EKS pod 생존 보장)
func InitPostgres() (*gorm.DB, error) {
	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		return nil, fmt.Errorf("DATABASE_URL environment variable is not set")
	}

	// Logger 설정
	logLevel := logger.Silent
	if os.Getenv("ENV") == "dev" {
		logLevel = logger.Info
	}

	gormConfig := &gorm.Config{
		Logger: logger.Default.LogMode(logLevel),
		NowFunc: func() time.Time {
			return time.Now().UTC()
		},
	}

	// 연결 시도 (타임아웃 설정)
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	var err error
	var conn *gorm.DB

	// 연결 시도
	done := make(chan bool, 1)
	go func() {
		conn, err = gorm.Open(postgres.Open(dsn), gormConfig)
		done <- true
	}()

	select {
	case <-ctx.Done():
		return nil, fmt.Errorf("database connection timeout")
	case <-done:
		if err != nil {
			return nil, fmt.Errorf("failed to connect to database: %w", err)
		}
	}

	sqlDB, err := conn.DB()
	if err != nil {
		return nil, fmt.Errorf("failed to get database instance: %w", err)
	}

	// Connection Pool 설정
	sqlDB.SetMaxIdleConns(10)
	sqlDB.SetMaxOpenConns(100)
	sqlDB.SetConnMaxLifetime(time.Hour)

	// Auto Migration
	if err := autoMigrate(conn); err != nil {
		return nil, fmt.Errorf("failed to run migrations: %w", err)
	}

	// 성공 시 전역 변수 업데이트
	dbMux.Lock()
	db = conn
	DBReady = true
	dbMux.Unlock()

	log.Println("✅ PostgreSQL connected and migrated successfully")
	return conn, nil
}

// InitPostgresAsync는 백그라운드에서 DB 연결을 시도합니다.
// 앱 시작을 블로킹하지 않고 연결 재시도를 계속합니다.
func InitPostgresAsync(retryInterval time.Duration) {
	go func() {
		for {
			if IsDBReady() {
				return // 이미 연결됨
			}

			_, err := InitPostgres()
			if err != nil {
				log.Printf("⚠️  DB connection failed, retrying in %v: %v\n", retryInterval, err)
				time.Sleep(retryInterval)
				continue
			}
			return
		}
	}()
}

// autoMigrate runs database migrations
func autoMigrate(db *gorm.DB) error {
	return db.AutoMigrate(
		&model.Chat{},
		&model.ChatParticipant{},
		&model.Message{},
		&model.MessageRead{},
		&model.UserPresence{},
	)
}

// GetDB returns the database instance (nil if not connected)
func GetDB() *gorm.DB {
	dbMux.RLock()
	defer dbMux.RUnlock()
	return db
}

// IsDBReady returns whether DB is connected
func IsDBReady() bool {
	dbMux.RLock()
	defer dbMux.RUnlock()
	return DBReady && db != nil
}