package com.washy.dify.user.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类
 * 必须从配置文件读取密钥，不允许使用默认密钥
 */
public final class JwtUtil {

    private static String SECRET;
    private static long EXPIRE_MILLIS;
    private static Key CACHED_KEY;

    static {
        InputStream input = null;
        try {
            Yaml yaml = new Yaml();
            input = JwtUtil.class.getClassLoader().getResourceAsStream("application.yml");
            if (input == null) {
                throw new RuntimeException("JWT配置文件未找到: application.yml");
            }
            
            Map<String, Object> yamlMap = yaml.load(input);
            Map<String, Object> jwt = (Map<String, Object>) yamlMap.get("jwt");
            
            if (jwt == null) {
                throw new RuntimeException("JWT配置缺失: 请在application.yml中配置jwt节点");
            }

            SECRET = (String) jwt.get("secret");
            if (SECRET == null || SECRET.trim().isEmpty()) {
                throw new RuntimeException("JWT密钥未配置: jwt.secret不能为空");
            }
            
            if (SECRET.length() < 32) {
                throw new RuntimeException("JWT密钥长度不足: 至少需要32个字符");
            }

            String expireStr = String.valueOf(jwt.get("expire"));
            long expireSec = Long.parseLong(expireStr);
            EXPIRE_MILLIS = expireSec * 1000;
            
            CACHED_KEY = generateKey(SECRET);
            
        } catch (NumberFormatException e) {
            throw new RuntimeException("JWT过期时间配置错误: jwt.expire必须是数字", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("加载JWT配置失败", e);
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private static Key generateKey(String secret) {
        byte[] keyBytes = secret.getBytes();
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT密钥至少需要32字节");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    private JwtUtil() {}

    public static String generateToken(Long userId, String username) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("username", username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_MILLIS))
                .signWith(CACHED_KEY)
                .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(CACHED_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public static boolean validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("userId", Long.class) : null;
    }

    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims != null ? claims.get("username", String.class) : null;
    }

    public static long getExpireMillis() {
        return EXPIRE_MILLIS;
    }
}