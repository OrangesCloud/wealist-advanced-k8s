package OrangeCloud.AuthService.oauth;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Getter
public class CustomOAuth2User implements OAuth2User {

    private final OAuth2User oAuth2User;
    private final UUID userId;
    private final String email;
    private final String name;

    public CustomOAuth2User(OAuth2User oAuth2User, UUID userId, String email, String name) {
        this.oAuth2User = oAuth2User;
        this.userId = userId;
        this.email = email;
        this.name = name;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return oAuth2User.getAttributes();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return oAuth2User.getAuthorities();
    }

    @Override
    public String getName() {
        return name;
    }
}
