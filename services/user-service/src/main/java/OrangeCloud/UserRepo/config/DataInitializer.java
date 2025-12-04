package OrangeCloud.UserRepo.config;

import OrangeCloud.UserRepo.entity.*;
import OrangeCloud.UserRepo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final Environment environment;

    @Bean
    @Transactional
    public CommandLineRunner initializeData() {
        return args -> {
            String[] activeProfiles = environment.getActiveProfiles();
            boolean isDev = Arrays.asList(activeProfiles).contains("dev")
                    || Arrays.asList(activeProfiles).contains("local")
                    || activeProfiles.length == 0;

            if (!isDev) {
                log.info("⏭️ Production environment detected. Skipping dummy data initialization.");
                return;
            }

            if (userRepository.count() > 0) {
                log.info("✅ Database already has data. Skipping initialization.");
                return;
            }

            log.info("🚀 Starting dummy data initialization...");

            // 1️⃣ User 생성
            List<User> tempUsers = new ArrayList<>();
            for (int i = 1; i <= 50; i++) {
                User user = User.builder()
                        .email("user" + i + "@example.com")
                        .provider("google")
                        .googleId("google-id-" + String.format("%03d", i))
                        .isActive(true)
                        .build();
                tempUsers.add(user);
            }
            userRepository.saveAll(tempUsers);

            // ✅ 저장된 엔티티를 DB에서 다시 불러오기 (UUID 반영됨)
            List<User> users = userRepository.findAll();
            log.info("✅ Created {} users.", users.size());

            // 2️⃣ Workspace 생성 (각 5명 그룹의 첫 번째 유저가 owner)
            List<Workspace> workspaces = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                User owner = users.get(i * 5); // 각 그룹 첫 번째 유저를 owner로 지정

                Workspace ws = Workspace.builder()
                        .workspaceName("테스트 워크스페이스 " + (i + 1))
                        .workspaceDescription("이것은 테스트 워크스페이스 " + (i + 1) + "입니다.")
                        .ownerId(owner.getUserId()) // ✅ 정확히 userId 연결
                        .isActive(true)
                        .build();

                workspaces.add(ws);
            }
            workspaceRepository.saveAll(workspaces);

            List<Workspace> savedWorkspaces = workspaceRepository.findAll();
            log.info("✅ Created {} workspaces.", savedWorkspaces.size());

            // 3️⃣ UserProfile + WorkspaceMember 생성
            List<UserProfile> profiles = new ArrayList<>();
            List<WorkspaceMember> members = new ArrayList<>();

            for (int w = 0; w < savedWorkspaces.size(); w++) {
                Workspace ws = savedWorkspaces.get(w);

                for (int j = 0; j < 5; j++) {
                    int userIdx = w * 5 + j;
                    if (userIdx >= users.size()) break;

                    User user = users.get(userIdx);

                    // ➤ UserProfile
                    UserProfile profile = UserProfile.builder()
                            .userId(user.getUserId())
                            .workspaceId(ws.getWorkspaceId())
                            .nickName("테스터" + (userIdx + 1))
                            .email(user.getEmail())
                            .profileImageUrl("https://i.pravatar.cc/150?img=" + (userIdx + 1))
                            .build();
                    profiles.add(profile);

                    // ➤ WorkspaceMember
                    WorkspaceMember member = WorkspaceMember.builder()
                            .userId(user.getUserId())
                            .workspaceId(ws.getWorkspaceId())
                            .role(j == 0 ? WorkspaceMember.WorkspaceRole.OWNER : WorkspaceMember.WorkspaceRole.MEMBER)
                            .isDefault(j == 0)
                            .build();
                    members.add(member);
                }
            }

            userProfileRepository.saveAll(profiles);
            workspaceMemberRepository.saveAll(members);

            log.info("✅ Created {} user profiles.", profiles.size());
            log.info("✅ Created {} workspace members.", members.size());
            log.info("🎉 Dummy data initialization finished successfully.");
        };
    }
}
