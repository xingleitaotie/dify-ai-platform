package com.washy.dify.common.web;

import com.washy.dify.common.context.UserContext;
import com.washy.dify.common.context.UserContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class UserContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String userIdHeader = request.getHeader("X-User-Id");
            String usernameHeader = request.getHeader("X-User-Name");

            if (userIdHeader != null && !userIdHeader.isEmpty()) {
                Long userId = Long.parseLong(userIdHeader);
                UserContextHolder.setContext(new UserContext(userId, usernameHeader));
            } else {
                // 未认证用户（匿名）
                UserContextHolder.setContext(new UserContext(null, "anonymous"));
            }

            filterChain.doFilter(request, response);
        } finally {
            // 清理 ThreadLocal，防止内存泄露
            UserContextHolder.clear();
        }
    }
}