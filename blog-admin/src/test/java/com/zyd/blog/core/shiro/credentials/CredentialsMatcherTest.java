package com.zyd.blog.core.shiro.credentials;

import com.zyd.blog.util.PasswordUtil;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.util.ByteSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CredentialsMatcher 密码凭证匹配测试")
class CredentialsMatcherTest {

    private CredentialsMatcher credentialsMatcher;

    @BeforeEach
    void setUp() {
        credentialsMatcher = new CredentialsMatcher();
    }

    @Test
    @DisplayName("密码匹配 - 正确密码返回true")
    void doCredentialsMatch_correctPassword_returnsTrue() throws Exception {
        String username = "admin";
        String rawPassword = "123456";
        String encryptedPassword = PasswordUtil.encrypt(rawPassword, username);

        UsernamePasswordToken token = new UsernamePasswordToken(username, rawPassword);
        AuthenticationInfo info = new SimpleAuthenticationInfo(
                1L,
                encryptedPassword,
                ByteSource.Util.bytes(username),
                "testRealm"
        );

        boolean result = credentialsMatcher.doCredentialsMatch(token, info);
        assertTrue(result);
    }

    @Test
    @DisplayName("密码匹配 - 错误密码返回false")
    void doCredentialsMatch_wrongPassword_returnsFalse() throws Exception {
        String username = "admin";
        String rawPassword = "123456";
        String encryptedPassword = PasswordUtil.encrypt(rawPassword, username);

        UsernamePasswordToken token = new UsernamePasswordToken(username, "wrongpassword");
        AuthenticationInfo info = new SimpleAuthenticationInfo(
                1L,
                encryptedPassword,
                ByteSource.Util.bytes(username),
                "testRealm"
        );

        boolean result = credentialsMatcher.doCredentialsMatch(token, info);
        assertFalse(result);
    }

    @Test
    @DisplayName("密码匹配 - 数据库中密码格式异常时返回false")
    void doCredentialsMatch_invalidDbPassword_returnsFalse() {
        UsernamePasswordToken token = new UsernamePasswordToken("admin", "123456");
        // 数据库中存储了无法解密的非法密码
        AuthenticationInfo info = new SimpleAuthenticationInfo(
                1L,
                "not-a-valid-encrypted-password",
                ByteSource.Util.bytes("admin"),
                "testRealm"
        );

        boolean result = credentialsMatcher.doCredentialsMatch(token, info);
        assertFalse(result);
    }

    @Test
    @DisplayName("密码匹配 - 不同用户名(salt)的密码不能互相匹配")
    void doCredentialsMatch_differentSalt_returnsFalse() throws Exception {
        String encryptedForAdmin = PasswordUtil.encrypt("123456", "admin");

        // 使用 user 作为用户名(salt)去匹配 admin 的加密密码
        UsernamePasswordToken token = new UsernamePasswordToken("user", "123456");
        AuthenticationInfo info = new SimpleAuthenticationInfo(
                1L,
                encryptedForAdmin,
                ByteSource.Util.bytes("user"),
                "testRealm"
        );

        boolean result = credentialsMatcher.doCredentialsMatch(token, info);
        assertFalse(result);
    }
}
