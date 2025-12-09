package database

import (
	"chat-service/internal/config"
	"chat-service/internal/domain"
	"fmt"
	"log"
	"os"
	"sync"
	"time"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

var (
	globalDB *gorm.DB
	dbMutex  sync.RWMutex
)

// GetDB returns the current database connection
func GetDB() *gorm.DB {
	dbMutex.RLock()
	defer dbMutex.RUnlock()
	return globalDB
}

// SetDB sets the global database connection
func SetDB(db *gorm.DB) {
	dbMutex.Lock()
	defer dbMutex.Unlock()
	globalDB = db
}

// IsConnected returns true if database is connected
func IsConnected() bool {
	db := GetDB()
	if db == nil {
		return false
	}
	sqlDB, err := db.DB()
	if err != nil {
		return false
	}
	return sqlDB.Ping() == nil
}

// NewAsync creates a database connection asynchronously with retries
func NewAsync(cfg *config.Config, retryInterval time.Duration) {
	go func() {
		for {
			db, err := NewDB(cfg)
			if err == nil {
				SetDB(db)
				fmt.Println("Database connected successfully (async)")
				return
			}
			fmt.Printf("Failed to connect to database, retrying in %v: %v\n", retryInterval, err)
			time.Sleep(retryInterval)
		}
	}()
}

func NewDB(cfg *config.Config) (*gorm.DB, error) {
	logLevel := logger.Silent
	if cfg.Server.Env == "dev" || cfg.Server.Env == "development" {
		logLevel = logger.Info
	}

	gormLogger := logger.New(
		log.New(os.Stdout, "\r\n", log.LstdFlags),
		logger.Config{
			SlowThreshold:             time.Second,
			LogLevel:                  logLevel,
			IgnoreRecordNotFoundError: true,
			Colorful:                  true,
		},
	)

	db, err := gorm.Open(postgres.Open(cfg.Database.URL), &gorm.Config{
		Logger: gormLogger,
	})
	if err != nil {
		return nil, err
	}

	// Configure connection pool
	sqlDB, err := db.DB()
	if err != nil {
		return nil, err
	}

	sqlDB.SetMaxIdleConns(10)
	sqlDB.SetMaxOpenConns(100)
	sqlDB.SetConnMaxLifetime(time.Hour)

	// Auto migrate
	if err := db.AutoMigrate(
		&domain.Chat{},
		&domain.ChatParticipant{},
		&domain.Message{},
		&domain.MessageRead{},
		&domain.UserPresence{},
	); err != nil {
		return nil, err
	}

	// Create indexes and constraints
	createIndexes(db)

	return db, nil
}

func createIndexes(db *gorm.DB) {
	// Unique constraint for chat participants
	db.Exec(`CREATE UNIQUE INDEX IF NOT EXISTS idx_participant_unique
		ON chat_participants (chat_id, user_id) WHERE is_active = true`)

	// Index for messages by chat and time
	db.Exec(`CREATE INDEX IF NOT EXISTS idx_messages_chat_created
		ON messages (chat_id, created_at DESC)`)

	// Index for presence
	db.Exec(`CREATE INDEX IF NOT EXISTS idx_presence_workspace_status
		ON user_presences (workspace_id, status)`)
}
