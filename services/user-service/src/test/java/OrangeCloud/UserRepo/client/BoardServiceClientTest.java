package OrangeCloud.UserRepo.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BoardServiceClient.
 * Tests REST API calls with MockRestTemplate, authentication header inclusion,
 * and error response handling.
 * Requirements: 8.1, 8.3
 */
@ExtendWith(MockitoExtension.class)
class BoardServiceClientTest {

        @Mock
        private RestTemplate restTemplate;

        private BoardServiceClient boardServiceClient;

        private static final String BOARD_SERVICE_URL = "http://localhost:8000";
        private static final String AUTH_TOKEN = "test-jwt-token";

        @BeforeEach
        void setUp() {
                boardServiceClient = new BoardServiceClient(restTemplate);
                ReflectionTestUtils.setField(boardServiceClient, "boardServiceUrl", BOARD_SERVICE_URL);
        }

        @Test
        void createProject_shouldReturnProjectId_whenSuccessful() {
                // Given
                UUID workspaceId = UUID.randomUUID();
                UUID projectId = UUID.randomUUID();

                CreateProjectRequest request = CreateProjectRequest.builder()
                                .workspaceId(workspaceId)
                                .name("Test Project")
                                .description("Test Description")
                                .build();

                ProjectResponse.ProjectData projectData = ProjectResponse.ProjectData.builder()
                                .id(projectId)
                                .workspaceId(workspaceId)
                                .name("Test Project")
                                .description("Test Description")
                                .createdAt(LocalDateTime.now())
                                .build();

                ProjectResponse response = ProjectResponse.builder()
                                .data(projectData)
                                .requestId(UUID.randomUUID().toString())
                                .build();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class),
                                eq(ProjectResponse.class))).thenReturn(ResponseEntity.ok(response));

                // When
                UUID result = boardServiceClient.createProject(request, AUTH_TOKEN);

                // Then
                assertThat(result).isEqualTo(projectId);

                ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate).exchange(
                                eq(BOARD_SERVICE_URL + "/api/projects"),
                                eq(HttpMethod.POST),
                                entityCaptor.capture(),
                                eq(ProjectResponse.class));

                HttpEntity<?> capturedEntity = entityCaptor.getValue();
                assertThat(capturedEntity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
                assertThat(capturedEntity.getHeaders().get(HttpHeaders.AUTHORIZATION))
                                .containsExactly("Bearer " + AUTH_TOKEN);
        }

        @Test
        void createProject_shouldThrowException_whenResponseIsNull() {
                // Given
                CreateProjectRequest request = CreateProjectRequest.builder()
                                .workspaceId(UUID.randomUUID())
                                .name("Test Project")
                                .build();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class),
                                eq(ProjectResponse.class))).thenReturn(ResponseEntity.ok(null));

                // When & Then
                assertThatThrownBy(() -> boardServiceClient.createProject(request, AUTH_TOKEN))
                                .isInstanceOf(BoardServiceClient.BoardServiceException.class)
                                .hasMessageContaining("null response");
        }

        @Test
        void createProject_shouldThrowException_whenHttpClientError() {
                // Given
                CreateProjectRequest request = CreateProjectRequest.builder()
                                .workspaceId(UUID.randomUUID())
                                .name("Test Project")
                                .build();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class),
                                eq(ProjectResponse.class)))
                                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request"));

                // When & Then
                assertThatThrownBy(() -> boardServiceClient.createProject(request, AUTH_TOKEN))
                                .isInstanceOf(BoardServiceClient.BoardServiceException.class)
                                .hasMessageContaining("Failed to create project");
        }

        @Test
        void createBoard_shouldReturnBoardId_whenSuccessful() {
                // Given
                UUID projectId = UUID.randomUUID();
                UUID boardId = UUID.randomUUID();
                UUID authorId = UUID.randomUUID();
                UUID assigneeId = UUID.randomUUID();

                CreateBoardRequest request = CreateBoardRequest.builder()
                                .projectId(projectId)
                                .assigneeId(assigneeId)
                                .title("Test Board")
                                .content("Test Content")
                                .startDate(LocalDateTime.now())
                                .dueDate(LocalDateTime.now().plusDays(7))
                                .build();

                BoardResponse.BoardData boardData = BoardResponse.BoardData.builder()
                                .id(boardId)
                                .projectId(projectId)
                                .title("Test Board")
                                .content("Test Content")
                                .createdAt(LocalDateTime.now())
                                .build();

                BoardResponse response = BoardResponse.builder()
                                .data(boardData)
                                .requestId(UUID.randomUUID().toString())
                                .build();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class),
                                eq(BoardResponse.class))).thenReturn(ResponseEntity.ok(response));

                // When
                UUID result = boardServiceClient.createBoard(request, AUTH_TOKEN);

                // Then
                assertThat(result).isEqualTo(boardId);

                ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate).exchange(
                                eq(BOARD_SERVICE_URL + "/api/boards"),
                                eq(HttpMethod.POST),
                                entityCaptor.capture(),
                                eq(BoardResponse.class));

                HttpEntity<?> capturedEntity = entityCaptor.getValue();
                assertThat(capturedEntity.getHeaders().get(HttpHeaders.AUTHORIZATION))
                                .containsExactly("Bearer " + AUTH_TOKEN);
        }

        @Test
        void createComment_shouldReturnCommentId_whenSuccessful() {
                // Given
                UUID boardId = UUID.randomUUID();
                UUID commentId = UUID.randomUUID();

                CreateCommentRequest request = CreateCommentRequest.builder()
                                .boardId(boardId)
                                .content("Test Comment")
                                .build();

                CommentResponse.CommentData commentData = CommentResponse.CommentData.builder()
                                .id(commentId)
                                .boardId(boardId)
                                .content("Test Comment")
                                .createdAt(LocalDateTime.now())
                                .build();

                CommentResponse response = CommentResponse.builder()
                                .data(commentData)
                                .requestId(UUID.randomUUID().toString())
                                .build();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class),
                                eq(CommentResponse.class))).thenReturn(ResponseEntity.ok(response));

                // When
                UUID result = boardServiceClient.createComment(request, AUTH_TOKEN);

                // Then
                assertThat(result).isEqualTo(commentId);

                ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
                verify(restTemplate).exchange(
                                eq(BOARD_SERVICE_URL + "/api/comments"),
                                eq(HttpMethod.POST),
                                entityCaptor.capture(),
                                eq(CommentResponse.class));

                HttpEntity<?> capturedEntity = entityCaptor.getValue();
                assertThat(capturedEntity.getHeaders().get(HttpHeaders.AUTHORIZATION))
                                .containsExactly("Bearer " + AUTH_TOKEN);
        }

        @Test
        void createComment_shouldThrowException_whenHttpClientError() {
                // Given
                CreateCommentRequest request = CreateCommentRequest.builder()
                                .boardId(UUID.randomUUID())
                                .content("Test Comment")
                                .build();

                when(restTemplate.exchange(
                                anyString(),
                                eq(HttpMethod.POST),
                                any(HttpEntity.class),
                                eq(CommentResponse.class)))
                                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND, "Not Found"));

                // When & Then
                assertThatThrownBy(() -> boardServiceClient.createComment(request, AUTH_TOKEN))
                                .isInstanceOf(BoardServiceClient.BoardServiceException.class)
                                .hasMessageContaining("Failed to create comment");
        }
}
