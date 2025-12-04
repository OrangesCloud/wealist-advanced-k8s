package OrangeCloud.UserRepo.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for SampleDataGenerator.
 * Tests verify that generated data comes from predefined lists and meets quality requirements.
 */
class SampleDataGeneratorTest {

    private SampleDataGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SampleDataGenerator();
    }

    @Test
    void generateKoreanName_shouldReturnNonEmptyString() {
        String name = generator.generateKoreanName();
        assertThat(name).isNotNull().isNotEmpty();
    }

    @Test
    void generateKoreanName_shouldReturnKoreanCharacters() {
        String name = generator.generateKoreanName();
        // Korean characters are in the Unicode range AC00-D7AF
        assertThat(name).matches("^[가-힣]+$");
    }

    @Test
    void generateKoreanName_shouldReturnFromPredefinedList() {
        Set<String> generatedNames = new HashSet<>();
        // Generate 100 names to get good coverage
        for (int i = 0; i < 100; i++) {
            generatedNames.add(generator.generateKoreanName());
        }
        // Should have at most 10 unique names (the size of KOREAN_NAMES array)
        assertThat(generatedNames).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void generateProjectName_shouldReturnNonEmptyString() {
        String projectName = generator.generateProjectName();
        assertThat(projectName).isNotNull().isNotEmpty();
    }

    @Test
    void generateProjectName_shouldReturnFromPredefinedList() {
        Set<String> generatedNames = new HashSet<>();
        // Generate 50 names to get good coverage
        for (int i = 0; i < 50; i++) {
            generatedNames.add(generator.generateProjectName());
        }
        // Should have at most 2 unique names (the size of PROJECT_NAMES array)
        assertThat(generatedNames).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void generateBoardTitle_shouldReturnNonEmptyString() {
        String title = generator.generateBoardTitle();
        assertThat(title).isNotNull().isNotEmpty();
    }

    @Test
    void generateBoardTitle_shouldReturnFromPredefinedList() {
        Set<String> generatedTitles = new HashSet<>();
        // Generate 100 titles to get good coverage
        for (int i = 0; i < 100; i++) {
            generatedTitles.add(generator.generateBoardTitle());
        }
        // Should have at most 20 unique titles (the size of BOARD_TITLES array)
        assertThat(generatedTitles).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void generateBoardCustomFields_shouldContainStatusAndPriority() {
        Map<String, Object> customFields = generator.generateBoardCustomFields();
        
        assertThat(customFields).isNotNull();
        assertThat(customFields).containsKeys("status", "priority");
    }

    @Test
    void generateBoardCustomFields_shouldHaveValidStatus() {
        Map<String, Object> customFields = generator.generateBoardCustomFields();
        
        String status = (String) customFields.get("status");
        assertThat(status).isIn("TODO", "IN_PROGRESS", "DONE");
    }

    @Test
    void generateBoardCustomFields_shouldHaveValidPriority() {
        Map<String, Object> customFields = generator.generateBoardCustomFields();
        
        String priority = (String) customFields.get("priority");
        assertThat(priority).isIn("LOW", "MEDIUM", "HIGH");
    }

    @Test
    void generateCommentContent_shouldReturnNonEmptyString() {
        String content = generator.generateCommentContent();
        assertThat(content).isNotNull().isNotEmpty();
    }

    @Test
    void generateCommentContent_shouldReturnFromPredefinedList() {
        Set<String> generatedContents = new HashSet<>();
        // Generate 100 comments to get good coverage
        for (int i = 0; i < 100; i++) {
            generatedContents.add(generator.generateCommentContent());
        }
        // Should have at most 8 unique contents (the size of COMMENT_CONTENTS array)
        assertThat(generatedContents).hasSizeLessThanOrEqualTo(8);
    }

    @Test
    void generateRandomDate_shouldReturnDateWithinRange() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime generated = generator.generateRandomDate(0, 30);
        
        assertThat(generated).isNotNull();
        assertThat(generated).isAfterOrEqualTo(now.minusMinutes(1)); // Allow small time difference
        assertThat(generated).isBeforeOrEqualTo(now.plusDays(30).plusMinutes(1));
    }

    @Test
    void generateRandomDate_shouldHandleNegativeDays() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime generated = generator.generateRandomDate(-30, 0);
        
        assertThat(generated).isNotNull();
        assertThat(generated).isAfterOrEqualTo(now.minusDays(30).minusMinutes(1));
        assertThat(generated).isBeforeOrEqualTo(now.plusMinutes(1));
    }

    @Test
    void generateRandomDate_shouldHandleMixedRange() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime generated = generator.generateRandomDate(-15, 15);
        
        assertThat(generated).isNotNull();
        assertThat(generated).isAfterOrEqualTo(now.minusDays(15).minusMinutes(1));
        assertThat(generated).isBeforeOrEqualTo(now.plusDays(15).plusMinutes(1));
    }

    @Test
    void generateRandomDate_shouldThrowExceptionWhenMinGreaterThanMax() {
        assertThatThrownBy(() -> generator.generateRandomDate(30, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minDays must be less than or equal to maxDays");
    }

    @Test
    void generateRandomDate_shouldHandleSameMinAndMax() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime generated = generator.generateRandomDate(10, 10);
        
        assertThat(generated).isNotNull();
        // Should be exactly 10 days from now (with small time tolerance)
        LocalDateTime expected = now.plusDays(10);
        assertThat(generated).isAfterOrEqualTo(expected.minusMinutes(1));
        assertThat(generated).isBeforeOrEqualTo(expected.plusMinutes(1));
    }
}
