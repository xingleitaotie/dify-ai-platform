package com.washy.dify.common.web;

import com.washy.dify.common.context.UserContext;
import com.washy.dify.common.context.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Slf4j
public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String userIdHeader = request.getHeader("X-User-Id");
            String usernameHeader = request.getHeader("X-User-Name");

            if (userIdHeader != null && !userIdHeader.trim().isEmpty()) {
                try {
                    Long userId = Long.parseLong(userIdHeader.trim());
                    String username = (usernameHeader != null && !usernameHeader.trim().isEmpty()) 
                            ? usernameHeader.trim() : "anonymous";
                    UserContextHolder.setContext(new UserContext(userId, username));
                } catch (NumberFormatException e) {
                    log.warn("用户ID格式错误: {}", userIdHeader);
                    UserContextHolder.setContext(new UserContext(null, "anonymous"));
                }
            } else {
                UserContextHolder.setContext(new UserContext(null, "anonymous"));
            }

            filterChain.doFilter(request, response);
        } finally {
            UserContextHolder.clear();
        }
    }
}