package database

import (
	"context"
	"fmt"
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
func NewAsync(cfg Config, retryInterval time.Duration) {
	go func() {
		for {
			db, err := New(cfg)
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

// Config holds database configuration
type Config struct {
	DSN             string
	MaxOpenConns    int
	MaxIdleConns    int
	ConnMaxLifetime time.Duration
}

// New creates a new database connection
func New(cfg Config) (*gorm.DB, error) {
	// Configure GORM
	gormConfig := &gorm.Config{
		Logger: logger.Default.LogMode(logger.Silent),
		NowFunc: func() time.Time {
			return time.Now().UTC()
		},
	}

	// Open connection
	db, err := gorm.Open(postgres.Open(cfg.DSN), gormConfig)
	if err != nil {
		return nil, fmt.Errorf("failed to connect to database: %w", err)
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
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := sqlDB.PingContext(ctx); err != nil {
		return nil, fmt.Errorf("failed to ping database: %w", err)
	}

	return db, nil
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
