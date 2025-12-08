package client

import (
	"context"
	"fmt"
	"io"
	"strings"
	"time"

	appConfig "project-board-api/internal/config"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/config"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"
	"github.com/google/uuid"
)

// S3ClientInterface defines the interface for S3 operations
type S3ClientInterface interface {
	GenerateFileKey(entityType, workspaceID, fileExt string) (string, error)
	GeneratePresignedURL(ctx context.Context, entityType, workspaceID, fileName, contentType string) (string, string, error)
	UploadFile(ctx context.Context, key string, file io.Reader, contentType string) (string, error)
	DeleteFile(ctx context.Context, key string) error
	GetFileURL(key string) string
}

// S3Client wraps AWS S3 client and implements S3ClientInterface
type S3Client struct {
	client              *s3.Client
	presignClient       *s3.PresignClient       // For internal operations
	publicPresignClient *s3.PresignClient       // For browser-accessible presigned URLs
	bucket              string
	region              string
	endpoint            string // MinIO 사용 시 내부 엔드포인트 (Docker 내부 통신용)
	publicEndpoint      string // 브라우저 접근용 공개 엔드포인트
}

// NewS3Client creates a new S3 client
func NewS3Client(cfg *appConfig.S3Config) (*S3Client, error) {
	if cfg.Bucket == "" {
		return nil, fmt.Errorf("S3 bucket is required")
	}
	if cfg.Region == "" {
		return nil, fmt.Errorf("S3 region is required")
	}

	// Create AWS config
	var awsCfg aws.Config
	var err error

	// If endpoint is provided (for local MinIO), use custom endpoint resolver with explicit credentials
	if cfg.Endpoint != "" {
		// MinIO requires explicit credentials
		if cfg.AccessKey == "" || cfg.SecretKey == "" {
			return nil, fmt.Errorf("access key and secret key are required for MinIO endpoint")
		}

		// 🚨 [핵심 수정] Deprecated 함수로 복구: config.WithEndpointResolverWithOptions
		// 빌드 오류를 회피하기 위해, Docker 빌드 환경이 확실히 알고 있는 함수로 되돌립니다.
		awsCfg, err = config.LoadDefaultConfig(context.TODO(),
			config.WithRegion(cfg.Region),
			config.WithCredentialsProvider(credentials.NewStaticCredentialsProvider(
				cfg.AccessKey,
				cfg.SecretKey,
				"",
			)),
			config.WithEndpointResolverWithOptions(aws.EndpointResolverWithOptionsFunc( // 💡 Deprecated 함수 사용
				func(service, region string, options ...interface{}) (aws.Endpoint, error) {
					return aws.Endpoint{
						URL:               cfg.Endpoint,
						HostnameImmutable: true,
						SigningRegion:     cfg.Region,
					}, nil
				},
			)),
		)
	} else {
		// Use AWS SDK default credential chain (IAM role on EC2, ~/.aws/credentials locally)
		awsCfg, err = config.LoadDefaultConfig(context.TODO(),
			config.WithRegion(cfg.Region),
		)
	}

	if err != nil {
		return nil, fmt.Errorf("failed to load AWS config: %w", err)
	}

	// Create S3 client for internal operations
	s3Client := s3.NewFromConfig(awsCfg, func(o *s3.Options) {
		if cfg.Endpoint != "" {
			o.UsePathStyle = true // Required for MinIO
		}
	})

	// Create presign client for internal use
	presignClient := s3.NewPresignClient(s3Client)

	// Create a separate presign client for browser-accessible URLs
	// This uses the public endpoint so signatures are computed correctly
	var publicPresignClient *s3.PresignClient
	if cfg.PublicEndpoint != "" && cfg.Endpoint != "" {
		// Create a new S3 client with public endpoint for presigning
		publicAwsCfg, err := config.LoadDefaultConfig(context.TODO(),
			config.WithRegion(cfg.Region),
			config.WithCredentialsProvider(credentials.NewStaticCredentialsProvider(
				cfg.AccessKey,
				cfg.SecretKey,
				"",
			)),
			config.WithEndpointResolverWithOptions(aws.EndpointResolverWithOptionsFunc(
				func(service, region string, options ...interface{}) (aws.Endpoint, error) {
					return aws.Endpoint{
						URL:               cfg.PublicEndpoint,
						HostnameImmutable: true,
						SigningRegion:     cfg.Region,
					}, nil
				},
			)),
		)
		if err != nil {
			return nil, fmt.Errorf("failed to load public AWS config: %w", err)
		}

		publicS3Client := s3.NewFromConfig(publicAwsCfg, func(o *s3.Options) {
			o.UsePathStyle = true
		})
		publicPresignClient = s3.NewPresignClient(publicS3Client)
	} else {
		publicPresignClient = presignClient
	}

	return &S3Client{
		client:              s3Client,
		presignClient:       presignClient,
		publicPresignClient: publicPresignClient,
		bucket:              cfg.Bucket,
		region:              cfg.Region,
		endpoint:            cfg.Endpoint,
		publicEndpoint:      cfg.PublicEndpoint,
	}, nil
}

// GenerateFileKey generates a unique S3 file key
// Format: board/{entityType}/{workspaceId}/{year}/{month}/{uuid}_{timestamp}.ext
// entityType: "boards", "comments", "projects"
func (c *S3Client) GenerateFileKey(entityType, workspaceID, fileExt string) (string, error) {
	// Validate entityType
	validTypes := map[string]bool{
		"boards":   true,
		"comments": true,
		"projects": true,
	}
	if !validTypes[entityType] {
		return "", fmt.Errorf("invalid entity type: %s (must be 'boards', 'comments', or 'projects')", entityType)
	}

	now := time.Now()
	year := now.Format("2006")
	month := now.Format("01")
	fileUUID := uuid.New().String()
	timestamp := now.Unix()

	key := fmt.Sprintf("board/%s/%s/%s/%s/%s_%d%s",
		entityType, workspaceID, year, month, fileUUID, timestamp, fileExt)

	return key, nil
}

// GeneratePresignedURL generates a presigned URL for uploading a file to S3
// The URL expires in 5 minutes
// Uses publicPresignClient to ensure signature matches the public endpoint browsers will use
func (c *S3Client) GeneratePresignedURL(ctx context.Context, entityType, workspaceID, fileName, contentType string) (string, string, error) {
	// Extract file extension
	fileExt := ""
	for i := len(fileName) - 1; i >= 0; i-- {
		if fileName[i] == '.' {
			fileExt = fileName[i:]
			break
		}
	}

	// Generate file key
	fileKey, err := c.GenerateFileKey(entityType, workspaceID, fileExt)
	if err != nil {
		return "", "", fmt.Errorf("failed to generate file key: %w", err)
	}

	// Create presigned PUT request
	putObjectInput := &s3.PutObjectInput{
		Bucket:      aws.String(c.bucket),
		Key:         aws.String(fileKey),
		ContentType: aws.String(contentType),
	}

	// Generate presigned URL with 5 minute expiration
	// Use publicPresignClient which is configured with public endpoint
	// This ensures the signature is computed against the URL browsers will actually use
	presignedReq, err := c.publicPresignClient.PresignPutObject(ctx, putObjectInput, func(opts *s3.PresignOptions) {
		opts.Expires = 5 * time.Minute
	})
	if err != nil {
		return "", "", fmt.Errorf("failed to generate presigned URL: %w", err)
	}

	return presignedReq.URL, fileKey, nil
}

// UploadFile uploads a file to S3
func (c *S3Client) UploadFile(ctx context.Context, key string, file io.Reader, contentType string) (string, error) {
	_, err := c.client.PutObject(ctx, &s3.PutObjectInput{
		Bucket:      aws.String(c.bucket),
		Key:         aws.String(key),
		Body:        file,
		ContentType: aws.String(contentType),
	})
	if err != nil {
		return "", fmt.Errorf("failed to upload file to S3: %w", err)
	}

	// Generate file URL
	fileURL := fmt.Sprintf("https://%s.s3.%s.amazonaws.com/%s", c.bucket, c.region, key)
	return fileURL, nil
}

// DeleteFile deletes a file from S3
func (c *S3Client) DeleteFile(ctx context.Context, key string) error {
	_, err := c.client.DeleteObject(ctx, &s3.DeleteObjectInput{
		Bucket: aws.String(c.bucket),
		Key:    aws.String(key),
	})
	if err != nil {
		return fmt.Errorf("failed to delete file from S3: %w", err)
	}
	return nil
}

// GetFileURL returns the public URL for a file
// S3 Key를 기반으로 다운로드 가능한 URL을 생성합니다.
func (c *S3Client) GetFileURL(key string) string {
	// MinIO 환경인 경우 - publicEndpoint 사용 (브라우저 접근용)
	if c.publicEndpoint != "" {
		return fmt.Sprintf("%s/%s/%s", strings.TrimSuffix(c.publicEndpoint, "/"), c.bucket, key)
	}

	// MinIO 환경이지만 publicEndpoint가 없는 경우 - endpoint fallback
	if c.endpoint != "" {
		return fmt.Sprintf("%s/%s/%s", strings.TrimSuffix(c.endpoint, "/"), c.bucket, key)
	}

	// AWS S3 환경인 경우 (기본)
	return fmt.Sprintf("https://%s.s3.%s.amazonaws.com/%s", c.bucket, c.region, key)
}
