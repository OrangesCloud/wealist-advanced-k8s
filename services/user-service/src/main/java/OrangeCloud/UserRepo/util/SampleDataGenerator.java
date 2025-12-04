package OrangeCloud.UserRepo.util;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Utility component for generating realistic sample data for workspace seeding.
 * Provides methods to generate Korean names, project names, board titles, custom fields,
 * comments, and random dates within specified ranges.
 */
@Component
public class SampleDataGenerator {

    private final Random random = new Random();

    // Predefined Korean names for sample users
    private static final String[] KOREAN_NAMES = {
            "김철수", "이영희", "박민수", "정수진", "최동욱",
            "강서연", "윤지호", "임하늘", "한소희", "오준영"
    };

    // Predefined project names
    private static final String[] PROJECT_NAMES = {
            "웹 애플리케이션 개발",
            "모바일 앱 리뉴얼"
    };

    // Predefined board titles
    private static final String[] BOARD_TITLES = {
            "로그인 기능 구현", "회원가입 페이지 디자인", "데이터베이스 스키마 설계",
            "API 엔드포인트 개발", "프론트엔드 컴포넌트 작성", "테스트 코드 작성",
            "배포 파이프라인 구축", "성능 최적화", "보안 취약점 점검",
            "사용자 피드백 반영", "문서화 작업", "코드 리뷰",
            "버그 수정", "UI/UX 개선", "알림 기능 추가",
            "검색 기능 구현", "파일 업로드 기능", "권한 관리 시스템",
            "대시보드 개발", "리포트 생성 기능"
    };

    // Predefined board statuses
    private static final String[] BOARD_STATUSES = {
            "TODO", "IN_PROGRESS", "DONE"
    };

    // Predefined priorities
    private static final String[] PRIORITIES = {
            "LOW", "MEDIUM", "HIGH"
    };

    // Predefined comment contents
    private static final String[] COMMENT_CONTENTS = {
            "진행 상황 확인 부탁드립니다.",
            "이 부분은 다시 검토가 필요할 것 같습니다.",
            "좋은 아이디어네요!",
            "내일까지 완료 가능할까요?",
            "관련 문서를 공유해주세요.",
            "테스트 결과를 확인했습니다.",
            "수정 사항을 반영했습니다.",
            "추가 논의가 필요합니다."
    };

    /**
     * Generates a random Korean name from the predefined list.
     *
     * @return A Korean name string
     */
    public String generateKoreanName() {
        return KOREAN_NAMES[random.nextInt(KOREAN_NAMES.length)];
    }

    /**
     * Generates a random project name from the predefined list.
     *
     * @return A project name string
     */
    public String generateProjectName() {
        return PROJECT_NAMES[random.nextInt(PROJECT_NAMES.length)];
    }

    /**
     * Generates a random board title from the predefined list.
     *
     * @return A board title string
     */
    public String generateBoardTitle() {
        return BOARD_TITLES[random.nextInt(BOARD_TITLES.length)];
    }

    /**
     * Generates random custom fields for a board including status and priority.
     *
     * @return A map containing status and priority fields
     */
    public Map<String, Object> generateBoardCustomFields() {
        Map<String, Object> customFields = new HashMap<>();
        customFields.put("status", BOARD_STATUSES[random.nextInt(BOARD_STATUSES.length)]);
        customFields.put("priority", PRIORITIES[random.nextInt(PRIORITIES.length)]);
        return customFields;
    }

    /**
     * Generates random comment content from the predefined list.
     *
     * @return A comment content string
     */
    public String generateCommentContent() {
        return COMMENT_CONTENTS[random.nextInt(COMMENT_CONTENTS.length)];
    }

    /**
     * Generates a random date within the specified range from the current date.
     *
     * @param minDays Minimum number of days from now (can be negative for past dates)
     * @param maxDays Maximum number of days from now (can be negative for past dates)
     * @return A LocalDateTime within the specified range
     */
    public LocalDateTime generateRandomDate(int minDays, int maxDays) {
        if (minDays > maxDays) {
            throw new IllegalArgumentException("minDays must be less than or equal to maxDays");
        }
        
        int daysOffset = minDays + random.nextInt(maxDays - minDays + 1);
        return LocalDateTime.now().plusDays(daysOffset);
    }
}
