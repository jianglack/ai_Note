package com.ainote.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private JwtTokenProvider tokenProvider;
    private CustomUserDetailsService userDetailsService;
    private TokenService tokenService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        tokenProvider = mock(JwtTokenProvider.class);
        userDetailsService = mock(CustomUserDetailsService.class);
        tokenService = mock(TokenService.class);
        filter = new JwtAuthenticationFilter(tokenProvider, userDetailsService, tokenService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_validToken_setsAuthenticationAndContinuesChain() throws Exception {
        UserDetails userDetails = new User("alice", "hash", List.of());
        when(tokenProvider.validateToken("valid-token")).thenReturn(true);
        when(tokenProvider.getJtiFromToken("valid-token")).thenReturn("jti-1");
        when(tokenProvider.getUserIdFromToken("valid-token")).thenReturn("user-1");
        when(tokenService.validateToken("jti-1", "user-1")).thenReturn(true);
        when(userDetailsService.loadUserById("user-1")).thenReturn(userDetails);
        MockHttpServletRequest request = request("Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isSameAs(userDetails);
        assertThat(authentication.isAuthenticated()).isTrue();
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_missingToken_leavesContextEmptyAndContinuesChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenProvider, never()).validateToken(org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_invalidToken_leavesContextEmpty() throws Exception {
        when(tokenProvider.validateToken("bad-token")).thenReturn(false);
        MockHttpServletRequest request = request("Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(tokenService, never()).validateToken(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(userDetailsService, never()).loadUserById(org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilter_revokedToken_leavesContextEmpty() throws Exception {
        when(tokenProvider.validateToken("revoked-token")).thenReturn(true);
        when(tokenProvider.getJtiFromToken("revoked-token")).thenReturn("jti-1");
        when(tokenProvider.getUserIdFromToken("revoked-token")).thenReturn("user-1");
        when(tokenService.validateToken("jti-1", "user-1")).thenReturn(false);
        MockHttpServletRequest request = request("Bearer revoked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserById(org.mockito.ArgumentMatchers.anyString());
        verify(chain).doFilter(request, response);
    }

    @Test
    void getJwtFromRequest_onlyAcceptsBearerPrefix() {
        assertThat(JwtAuthenticationFilter.getJwtFromRequest(request("Bearer token"))).isEqualTo("token");
        assertThat(JwtAuthenticationFilter.getJwtFromRequest(request("Basic token"))).isNull();
    }

    @Test
    void getJwtFromRequest_readsCookieWhenBearerHeaderMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ai/chat/stream");
        request.setCookies(new Cookie(JwtAuthenticationFilter.AUTH_COOKIE_NAME, "cookie-token"));

        assertThat(JwtAuthenticationFilter.getJwtFromRequest(request)).isEqualTo("cookie-token");
    }

    @Test
    void getJwtFromRequest_prefersBearerHeaderOverCookie() {
        MockHttpServletRequest request = request("Bearer header-token");
        request.setCookies(new Cookie(JwtAuthenticationFilter.AUTH_COOKIE_NAME, "cookie-token"));

        assertThat(JwtAuthenticationFilter.getJwtFromRequest(request)).isEqualTo("header-token");
    }

    private static MockHttpServletRequest request(String authorizationHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/notes");
        request.addHeader("Authorization", authorizationHeader);
        return request;
    }
}
