package com.ainote.app.security;

import com.ainote.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTest {

    private UserRepository userRepository;
    private CustomUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new CustomUserDetailsService(userRepository);
    }

    @Test
    void loadUserByUsername_existingUser_returnsUserDetails() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("user-1", "alice")));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("hash");
        assertThat(details.getAuthorities()).isEmpty();
    }

    @Test
    void loadUserByUsername_missingUser_throwsUsernameNotFound() {
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found: missing");
    }

    @Test
    void loadUserById_existingUser_returnsUserDetails() {
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user("user-1", "alice")));

        UserDetails details = service.loadUserById("user-1");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("hash");
    }

    @Test
    void loadUserById_missingUser_throwsUsernameNotFound() {
        when(userRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserById("missing"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found: missing");
    }

    private static com.ainote.app.entity.User user(String id, String username) {
        return new com.ainote.app.entity.User(
                id,
                username,
                username + "@example.com",
                "hash",
                LocalDateTime.now());
    }
}
