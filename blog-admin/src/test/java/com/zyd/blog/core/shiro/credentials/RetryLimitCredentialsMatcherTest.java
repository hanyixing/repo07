package com.zyd.blog.core.shiro.credentials;

import com.zyd.blog.business.consts.SessionConst;
import com.zyd.blog.business.entity.User;
import com.zyd.blog.business.service.SysConfigService;
import com.zyd.blog.business.service.SysUserService;
import com.zyd.blog.util.PasswordUtil;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ByteSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RetryLimitCredentialsMatcher 登录重试限制测试")
class RetryLimitCredentialsMatcherTest {

    @Mock
    private RedisTemplate redisTemplate;
    @Mock
    private SysUserService userService;
    @Mock
    private SysConfigService configService;
    @Mock
    private ValueOperations valueOperations;

    private RetryLimitCredentialsMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new RetryLimitCredentialsMatcher();
        ReflectionTestUtils.setField(matcher, "redisTemplate", redisTemplate);
        ReflectionTestUtils.setField(matcher, "userService", userService);
        ReflectionTestUtils.setField(matcher, "configService", configService);
    }

    private Map<String, Object> getDefaultConfigs() {
        Map<String, Object> configs = new HashMap<>();
        configs.put("loginRetryNum", "5");
        configs.put("sessionTimeOut", "1");
        configs.put("sessionTimeOutUnit", "HOURS");
        return configs;
    }

    private User createTestUser() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword(PasswordUtil.encrypt("123456", "testuser"));
        return user;
    }

    private AuthenticationInfo createAuthInfo(User user) {
        return new SimpleAuthenticationInfo(
                user.getId(),
                user.getPassword(),
                ByteSource.Util.bytes(user.getUsername()),
                "testRealm"
        );
    }

    // ==================== 账号锁定测试 ====================

    @Test
    @DisplayName("登录 - 账号已被锁定时抛出ExcessiveAttemptsException")
    void doCredentialsMatch_accountLocked_throwsException() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        AuthenticationInfo info = createAuthInfo(user);
        when(userService.getByPrimaryKey(1L)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("shiro_is_lock_testuser")).thenReturn(true);
        when(redisTemplate.getExpire("shiro_is_lock_testuser")).thenReturn(3600L);

        UsernamePasswordToken token = new UsernamePasswordToken("testuser", "123456");

        assertThrows(ExcessiveAttemptsException.class, () -> matcher.doCredentialsMatch(token, info));
    }

    @Test
    @DisplayName("登录 - 账号被锁定且剩余时间小于1分钟时显示秒")
    void doCredentialsMatch_lockedShowsSeconds_throwsException() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        AuthenticationInfo info = createAuthInfo(user);
        when(userService.getByPrimaryKey(1L)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("shiro_is_lock_testuser")).thenReturn(true);
        when(redisTemplate.getExpire("shiro_is_lock_testuser")).thenReturn(30L);

        UsernamePasswordToken token = new UsernamePasswordToken("testuser", "123456");

        ExcessiveAttemptsException ex = assertThrows(ExcessiveAttemptsException.class,
                () -> matcher.doCredentialsMatch(token, info));
        assertTrue(ex.getMessage().contains("秒"));
    }

    @Test
    @DisplayName("登录 - 账号被锁定且剩余时间大于1小时时显示小时")
    void doCredentialsMatch_lockedShowsHours_throwsException() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        AuthenticationInfo info = createAuthInfo(user);
        when(userService.getByPrimaryKey(1L)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("shiro_is_lock_testuser")).thenReturn(true);
        when(redisTemplate.getExpire("shiro_is_lock_testuser")).thenReturn(7200L);

        UsernamePasswordToken token = new UsernamePasswordToken("testuser", "123456");

        ExcessiveAttemptsException ex = assertThrows(ExcessiveAttemptsException.class,
                () -> matcher.doCredentialsMatch(token, info));
        assertTrue(ex.getMessage().contains("小时"));
    }

    // ==================== 重试次数耗尽测试 ====================

    @Test
    @DisplayName("登录 - 重试次数耗尽时锁定账号并抛出异常")
    void doCredentialsMatch_retryExhausted_locksAndThrows() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");

        AuthenticationInfo info = createAuthInfo(user);
        when(userService.getByPrimaryKey(1L)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("shiro_is_lock_testuser")).thenReturn(false);
        when(configService.getConfigs()).thenReturn(getDefaultConfigs());
        // 第6次登录（loginRetryNum=5, retryCount = 5+1-6 = 0）
        when(valueOperations.get("shiro_login_count_testuser")).thenReturn("6");

        UsernamePasswordToken token = new UsernamePasswordToken("testuser", "123456");

        assertThrows(ExcessiveAttemptsException.class, () -> matcher.doCredentialsMatch(token, info));
        verify(valueOperations).set(eq("shiro_is_lock_testuser"), eq("LOCK"));
    }

    // ==================== 密码错误测试 ====================

    @Test
    @DisplayName("登录 - 密码错误时抛出AccountException并提示剩余次数")
    void doCredentialsMatch_wrongPassword_throwsAccountException() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("encrypted_wrong");

        AuthenticationInfo info = new SimpleAuthenticationInfo(
                user.getId(),
                "not-valid-encrypted",
                ByteSource.Util.bytes("testuser"),
                "testRealm"
        );

        when(userService.getByPrimaryKey(1L)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("shiro_is_lock_testuser")).thenReturn(false);
        when(configService.getConfigs()).thenReturn(getDefaultConfigs());
        // 第1次登录
        when(valueOperations.get("shiro_login_count_testuser")).thenReturn("1");

        UsernamePasswordToken token = new UsernamePasswordToken("testuser", "wrongpassword");

        AccountException ex = assertThrows(AccountException.class,
                () -> matcher.doCredentialsMatch(token, info));
        assertTrue(ex.getMessage().contains("还剩"));
    }

    // ==================== 登录成功测试 ====================

    @Test
    @DisplayName("登录 - 密码正确时清除计数并更新用户信息")
    void doCredentialsMatch_success_clearsCountAndUpdatesUser() throws Exception {
        User user = createTestUser();
        AuthenticationInfo info = createAuthInfo(user);

        // Set up Shiro SecurityManager for SecurityUtils.getSubject()
        DefaultSecurityManager securityManager = new DefaultSecurityManager();
        SecurityUtils.setSecurityManager(securityManager);

        when(userService.getByPrimaryKey(1L)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("shiro_is_lock_testuser")).thenReturn(false);
        when(configService.getConfigs()).thenReturn(getDefaultConfigs());
        when(valueOperations.get("shiro_login_count_testuser")).thenReturn("1");

        UsernamePasswordToken token = new UsernamePasswordToken("testuser", "123456");

        boolean result = matcher.doCredentialsMatch(token, info);

        assertTrue(result);
        // 验证清除了登录计数
        verify(redisTemplate).delete("shiro_login_count_testuser");
        // 验证更新了用户最后登录信息
        verify(userService).updateUserLastLoginInfo(user);

        // Clean up Shiro
        SecurityUtils.setSecurityManager(null);
    }

    // ==================== 配置默认值测试 ====================

    @Test
    @DisplayName("登录 - 配置值为空时使用默认重试次数(5次)")
    void doCredentialsMatch_emptyConfig_usesDefaults() {
        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("encrypted");

        AuthenticationInfo info = new SimpleAuthenticationInfo(
                user.getId(),
                "invalid-encrypted",
                ByteSource.Util.bytes("testuser"),
                "testRealm"
        );

        when(userService.getByPrimaryKey(1L)).thenReturn(user);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey("shiro_is_lock_testuser")).thenReturn(false);

        Map<String, Object> emptyConfigs = new HashMap<>();
        emptyConfigs.put("loginRetryNum", null);
        emptyConfigs.put("sessionTimeOut", null);
        emptyConfigs.put("sessionTimeOutUnit", null);
        when(configService.getConfigs()).thenReturn(emptyConfigs);

        // 第1次，retryCount = 5+1-1 = 5
        when(valueOperations.get("shiro_login_count_testuser")).thenReturn("1");

        UsernamePasswordToken token = new UsernamePasswordToken("testuser", "wrong");

        // 密码不正确会抛 AccountException，但不会抛 ExcessiveAttemptsException
        assertThrows(AccountException.class, () -> matcher.doCredentialsMatch(token, info));
    }
}
