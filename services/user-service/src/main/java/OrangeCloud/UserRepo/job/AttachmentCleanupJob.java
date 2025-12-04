package OrangeCloud.UserRepo.job;

import OrangeCloud.UserRepo.entity.Attachment;
import OrangeCloud.UserRepo.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 첨부파일 정리 작업
 * 만료된 임시 첨부파일을 자동으로 삭제합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttachmentCleanupJob {

    private final AttachmentService attachmentService;

    /**
     * 만료된 임시 첨부파일 정리
     * 매 시간 정각에 실행됩니다.
     */
    @Scheduled(cron = "0 0 * * * *")  // 매 시간 정각 (0분 0초)
    public void cleanupExpiredAttachments() {
        log.info("첨부파일 정리 작업 시작");

        try {
            // 만료된 임시 첨부파일 조회
            List<Attachment> expiredAttachments = attachmentService.findExpiredTempAttachments();

            if (expiredAttachments.isEmpty()) {
                log.info("정리할 만료된 첨부파일이 없습니다.");
                return;
            }

            log.info("만료된 첨부파일 발견 - count: {}", expiredAttachments.size());

            // 일괄 삭제
            attachmentService.deleteBatch(expiredAttachments);

            log.info("첨부파일 정리 작업 완료 - 삭제된 파일 수: {}", expiredAttachments.size());

        } catch (Exception e) {
            log.error("첨부파일 정리 작업 실패", e);
            // 예외를 던지지 않고 로그만 남김 (다음 스케줄에서 재시도)
        }
    }
}
