package com.vtchat.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "vtchat.auth_error";

    private static final Pattern MEDIA_PATH = Pattern.compile("^/api/media/(\\d{1,18})(?:/(?:download|view|thumbnail))?$");

    private static final Pattern KNOWLEDGE_PATH = Pattern.compile("^/api/ai/knowledge/documents/(\\d{1,18})/(?:download|view)$");

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                AuthenticatedUser user = jwtService.parseAccessToken(header.substring(7).trim());
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ExpiredJwtException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "TOKEN_EXPIRED");
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
                request.setAttribute(AUTH_ERROR_ATTRIBUTE, "INVALID_TOKEN");
            }
        }
        else if (header == null && "GET".equals(request.getMethod()) && request.getParameter("mt") != null) {
            authenticateMediaLink(request);
        }
        chain.doFilter(request, response);
    }

    private void authenticateMediaLink(HttpServletRequest request) {
        String uri = request.getRequestURI();
        Matcher media = MEDIA_PATH.matcher(uri);
        Matcher knowledge = KNOWLEDGE_PATH.matcher(uri);
        boolean isMedia = media.matches();
        boolean isKnowledge = !isMedia && knowledge.matches();
        if (!isMedia && !isKnowledge) {
            return;
        }
        try {
            AuthenticatedUser user = isKnowledge
                    ? jwtService.parseKnowledgeToken(request.getParameter("mt"), Long.valueOf(knowledge.group(1)))
                    : jwtService.parseMediaToken(request.getParameter("mt"), Long.valueOf(media.group(1)));
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        } catch (JwtException | IllegalArgumentException e) {
            SecurityContextHolder.clearContext();
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, "INVALID_TOKEN");
        }
    }
}
