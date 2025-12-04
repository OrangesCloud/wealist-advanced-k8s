package OrangeCloud.UserRepo.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * REST client for communicating with Board-Service.
 * Handles project, board, and comment creation via Board-Service API.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BoardServiceClient {

    private final RestTemplate restTemplate;

    @Value("${board-service.url:http://localhost:8000}")
    private String boardServiceUrl;

    /**
     * Creates a project in Board-Service.
     *
     * @param request   the project creation request
     * @param authToken JWT authentication token
     * @return the created project ID
     * @throws BoardServiceException if the API call fails
     */
    public UUID createProject(CreateProjectRequest request, String authToken) {
        String url = boardServiceUrl + "/api/projects";
        
        log.debug("Creating project via Board-Service: workspaceId={}, name={}", 
                  request.getWorkspaceId(), request.getName());

        try {
            HttpHeaders headers = createAuthHeaders(authToken);
            HttpEntity<CreateProjectRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<ProjectResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                ProjectResponse.class
            );

            if (response.getBody() != null && response.getBody().getData() != null) {
                UUID projectId = response.getBody().getData().getId();
                log.info("Project created successfully: projectId={}", projectId);
                return projectId;
            } else {
                log.error("Project creation returned null response body");
                throw new BoardServiceException("Project creation returned null response");
            }

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Failed to create project: status={}, body={}", 
                      e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BoardServiceException("Failed to create project: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error creating project", e);
            throw new BoardServiceException("Unexpected error creating project: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a board in Board-Service.
     *
     * @param request   the board creation request
     * @param authToken JWT authentication token
     * @return the created board ID
     * @throws BoardServiceException if the API call fails
     */
    public UUID createBoard(CreateBoardRequest request, String authToken) {
        String url = boardServiceUrl + "/api/boards";
        
        log.debug("Creating board via Board-Service: projectId={}, title={}", 
                  request.getProjectId(), request.getTitle());

        try {
            HttpHeaders headers = createAuthHeaders(authToken);
            HttpEntity<CreateBoardRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<BoardResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                BoardResponse.class
            );

            if (response.getBody() != null && response.getBody().getData() != null) {
                UUID boardId = response.getBody().getData().getId();
                log.info("Board created successfully: boardId={}", boardId);
                return boardId;
            } else {
                log.error("Board creation returned null response body");
                throw new BoardServiceException("Board creation returned null response");
            }

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Failed to create board: status={}, body={}", 
                      e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BoardServiceException("Failed to create board: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error creating board", e);
            throw new BoardServiceException("Unexpected error creating board: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a comment in Board-Service.
     *
     * @param request   the comment creation request
     * @param authToken JWT authentication token
     * @return the created comment ID
     * @throws BoardServiceException if the API call fails
     */
    public UUID createComment(CreateCommentRequest request, String authToken) {
        String url = boardServiceUrl + "/api/comments";
        
        log.debug("Creating comment via Board-Service: boardId={}", request.getBoardId());

        try {
            HttpHeaders headers = createAuthHeaders(authToken);
            HttpEntity<CreateCommentRequest> entity = new HttpEntity<>(request, headers);

            ResponseEntity<CommentResponse> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                CommentResponse.class
            );

            if (response.getBody() != null && response.getBody().getData() != null) {
                UUID commentId = response.getBody().getData().getId();
                log.info("Comment created successfully: commentId={}", commentId);
                return commentId;
            } else {
                log.error("Comment creation returned null response body");
                throw new BoardServiceException("Comment creation returned null response");
            }

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            log.error("Failed to create comment: status={}, body={}", 
                      e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BoardServiceException("Failed to create comment: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error creating comment", e);
            throw new BoardServiceException("Unexpected error creating comment: " + e.getMessage(), e);
        }
    }

    /**
     * Creates HTTP headers with authentication token.
     *
     * @param authToken JWT authentication token
     * @return configured HTTP headers
     */
    private HttpHeaders createAuthHeaders(String authToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(authToken);
        return headers;
    }

    /**
     * Exception thrown when Board-Service API calls fail.
     */
    public static class BoardServiceException extends RuntimeException {
        public BoardServiceException(String message) {
            super(message);
        }

        public BoardServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
