package OrangeCloud.UserRepo.oauth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class CustomOAuth2User implements OAuth2User {

    private final UUID userId;
    private final String email;
    private final String name;
    private final String googleId;
    private final Map<String, Object> attributes;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomOAuth2User(UUID userId, String email, String name, String googleId,
            Map<String, Object> attributes, Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.email = email;
        this.name = name;
        this.googleId = googleId;
        this.attributes = attributes;
        this.authorities = authorities;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        return name;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getGoogleId() {
        return googleId;
    }
}