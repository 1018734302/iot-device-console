package com.demo.iot.web;

import com.demo.iot.config.IotProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 对 /api/** 做 X-Token 校验，使用定长比较避免时序侧信道。
 */
@Component
public class TokenFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Token";
    private final IotProperties props;

    public TokenFilter(IotProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        String token = request.getHeader(HEADER);
        byte[] expected = props.apiToken().getBytes(StandardCharsets.UTF_8);
        byte[] actual = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"invalid token\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
