package OrangeCloud.UserRepo.dto.userprofile;

import OrangeCloud.UserRepo.entity.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter 
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "사용자 프로필 응답 DTO")
public class UserProfileResponse implements Serializable {
    private static final long serialVersionUID = 1L; 
    
    private UUID profileId;
    private UUID workspaceId;
    private UUID userId;
    private String nickName;
    private String email;
    private String profileImageUrl;
    
    @Schema(description = "프로필 이미지 첨부파일 정보 (없을 수 있음)")
    private AttachmentResponse profileImageAttachment;
    
    /**
     * Entity를 DTO로 변환 (attachment 없이)
     */
    public static UserProfileResponse from(UserProfile profile) {
        return UserProfileResponse.builder()
                .profileId(profile.getProfileId())
                .workspaceId(profile.getWorkspaceId())
                .userId(profile.getUserId())
                .nickName(profile.getNickName())
                .email(profile.getEmail())
                .profileImageUrl(profile.getProfileImageUrl())
                .profileImageAttachment(null)
                .build();
    }
    
    /**
     * Entity를 DTO로 변환 (attachment 포함)
     */
    public static UserProfileResponse from(UserProfile profile, AttachmentResponse attachment) {
        return UserProfileResponse.builder()
                .profileId(profile.getProfileId())
                .workspaceId(profile.getWorkspaceId())
                .userId(profile.getUserId())
                .nickName(profile.getNickName())
                .email(profile.getEmail())
                .profileImageUrl(profile.getProfileImageUrl())
                .profileImageAttachment(attachment)
                .build();
    }
}