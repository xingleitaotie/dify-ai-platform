package com.washy.dify.user.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.security.Key;
import java.util.Date;
import java.util.Map;

/**
 * 独立版 JWT 工具
 * 每个项目自己一份，读取自己的 application.yml
 * 不依赖 Spring / 不依赖 common / 纯静态
 */
public final class JwtUtil {

    private static String SECRET;
    private static long EXPIRE_MILLIS;

    static {
        try {
            Yaml yaml = new Yaml();
            InputStream input = JwtUtil.class.getClassLoader().getResourceAsStream("application.yml");
            Map<String, Object> yamlMap = yaml.load(input);
            Map<String, Object> jwt = (Map<String, Object>) yamlMap.get("jwt");

            SECRET = (String) jwt.get("secret");
            long expireSec = Long.parseLong(jwt.get("expire").toString());
            EXPIRE_MILLIS = expireSec * 1000;
        } catch (Exception e) {
            // 默认安全密钥（64位，满足HS512）
            SECRET = "aB3kT9sP7dF2gH5jK8lZxQ4wE6rT7yU1iO0pS9dF3gH5jK7lL8kF9sD2sA1fG7hJ9kL3z";
            EXPIRE_MILLIS = 86400 * 1000;
        }
    }

    private static Key getKey() {
        // 自动补齐到 64 位，永不报错密钥长度不足
        String key = SECRET;
        while (key.length() < 64) {
            key += "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        }
        key = key.substring(0, 64);
        return Keys.hmacShaKeyFor(key.getBytes());
    }

    private JwtUtil() {}

    public static String generateToken(Long userId, String username) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_MILLIS))
                .signWith(getKey())
                .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Long getUserId(String token) {
        return parseToken(token).get("userId", Long.class);
    }

    public static String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }
}