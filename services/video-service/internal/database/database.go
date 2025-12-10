package database

import (
	"fmt"
	"sync"
	"time"
	"video-service/internal/config"
	"video-service/internal/domain"

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
	var gormLogger logger.Interface
	if cfg.Server.Env == "production" {
		gormLogger = logger.Default.LogMode(logger.Silent)
	} else {
		gormLogger = logger.Default.LogMode(logger.Info)
	}

	db, err := gorm.Open(postgres.Open(cfg.Database.URL), &gorm.Config{
		Logger: gormLogger,
	})
	if err != nil {
		return nil, err
	}

	// Auto migrate
	if err := db.AutoMigrate(
		&domain.Room{},
		&domain.RoomParticipant{},
		&domain.CallHistory{},
		&domain.CallHistoryParticipant{},
		&domain.CallTranscript{},
	); err != nil {
		return nil, err
	}

	return db, nil
}
