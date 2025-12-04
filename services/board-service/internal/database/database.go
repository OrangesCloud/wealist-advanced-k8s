package database

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"
	"gorm.io/gorm/logger"
)

var (
	globalDB *gorm.DB
	dbMux    sync.RWMutex

	// DBReady는 DB 연결 상태를 나타냄
	DBReady = false
)

// Config holds database configuration
type Config struct {
	DSN             string
	MaxOpenConns    int
	MaxIdleConns    int
	ConnMaxLifetime time.Duration
}

// New creates a new database connection
// DB 연결 실패해도 에러만 반환하고 앱은 계속 실행됨 (EKS pod 생존 보장)
func New(cfg Config) (*gorm.DB, error) {
	// Configure GORM
	gormConfig := &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
		NowFunc: func() time.Time {
			return time.Now().UTC()
		},
	}

	// Open connection with timeout
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	var db *gorm.DB
	var err error

	done := make(chan bool, 1)
	go func() {
		db, err = gorm.Open(postgres.Open(cfg.DSN), gormConfig)
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

	// Get underlying SQL DB
	sqlDB, err := db.DB()
	if err != nil {
		return nil, fmt.Errorf("failed to get database instance: %w", err)
	}

	// Set connection pool settings
	sqlDB.SetMaxOpenConns(cfg.MaxOpenConns)
	sqlDB.SetMaxIdleConns(cfg.MaxIdleConns)
	sqlDB.SetConnMaxLifetime(cfg.ConnMaxLifetime)

	// Test connection with context timeout
	pingCtx, pingCancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer pingCancel()

	if err := sqlDB.PingContext(pingCtx); err != nil {
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	// 성공 시 전역 변수 업데이트
	dbMux.Lock()
	globalDB = db
	DBReady = true
	dbMux.Unlock()

	return db, nil
}

// NewAsync는 백그라운드에서 DB 연결을 시도합니다.
// 앱 시작을 블로킹하지 않고 연결 재시도를 계속합니다.
func NewAsync(cfg Config, retryInterval time.Duration) {
	go func() {
		for {
			if IsDBReady() {
				return // 이미 연결됨
			}

			_, err := New(cfg)
			if err != nil {
				log.Printf("⚠️  DB connection failed, retrying in %v: %v\n", retryInterval, err)
				time.Sleep(retryInterval)
				continue
			}
			log.Println("✅ PostgreSQL connected successfully (async)")
			return
		}
	}()
}

// GetDB returns the global database instance (nil if not connected)
func GetDB() *gorm.DB {
	dbMux.RLock()
	defer dbMux.RUnlock()
	return globalDB
}

// IsDBReady returns whether DB is connected
func IsDBReady() bool {
	dbMux.RLock()
	defer dbMux.RUnlock()
	return DBReady && globalDB != nil
}

// Close closes the database connection
func Close(db *gorm.DB) error {
	sqlDB, err := db.DB()
	if err != nil {
		return fmt.Errorf("failed to get database instance: %w", err)
	}

	if err := sqlDB.Close(); err != nil {
		return fmt.Errorf("failed to close database: %w", err)
	}

	return nil
}

// WithContext returns a new DB instance with context
func WithContext(db *gorm.DB, ctx context.Context) *gorm.DB {
	return db.WithContext(ctx)
}

// WithTimeout returns a new DB instance with timeout context
func WithTimeout(db *gorm.DB, timeout time.Duration) *gorm.DB {
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	// Note: In production, you should manage the cancel function properly
	// This is a simplified version for the initial implementation
	_ = cancel
	return db.WithContext(ctx)
}
