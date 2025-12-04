// chat-service/internal/client/user_client.go

package client

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

type UserClient interface {
	ValidateToken(ctx context.Context, token string) (*TokenValidationResponse, error)
	GetUserInfo(ctx context.Context, userID, token string) (*UserInfo, error)
}

type userClient struct {
	baseURL     string
	authBaseURL string // auth-service URL for token validation
	httpClient  *http.Client
}

type TokenValidationResponse struct {
	UserID  string `json:"userId"`
	Valid   bool   `json:"valid"`
	Message string `json:"message"`
}

type UserInfo struct {
	UserID          string `json:"userId"`
	Email           string `json:"email"`
	NickName        string `json:"nickName,omitempty"`
	ProfileImageURL string `json:"profileImageUrl,omitempty"`
}

func NewUserClient(baseURL string, authBaseURL string, timeout time.Duration) UserClient {
	return &userClient{
		baseURL:     baseURL,
		authBaseURL: authBaseURL,
		httpClient: &http.Client{
			Timeout: timeout,
		},
	}
}

// 토큰 검증은 auth-service에서 처리
func (c *userClient) ValidateToken(ctx context.Context, token string) (*TokenValidationResponse, error) {
	// auth-service의 /api/auth/validate 엔드포인트 호출
	url := fmt.Sprintf("%s/api/auth/validate", c.authBaseURL)

	// Request body 생성
	body := map[string]string{"token": token}
	jsonBody, err := json.Marshal(body)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal request body: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewReader(jsonBody))
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("validation failed: status=%d, body=%s", resp.StatusCode, string(body))
	}

	var result TokenValidationResponse
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	return &result, nil
}

func (c *userClient) GetUserInfo(ctx context.Context, userID, token string) (*UserInfo, error) {
	url := fmt.Sprintf("%s/users/%s", c.baseURL, userID)

	req, err := http.NewRequestWithContext(ctx, "GET", url, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}

	req.Header.Set("Authorization", fmt.Sprintf("Bearer %s", token))

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to send request: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("get user info failed: status=%d, body=%s", resp.StatusCode, string(body))
	}

	var result UserInfo
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("failed to decode response: %w", err)
	}

	return &result, nil
}