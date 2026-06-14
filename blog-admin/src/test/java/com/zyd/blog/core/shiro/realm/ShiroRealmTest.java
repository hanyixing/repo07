package com.zyd.blog.core.shiro.realm;

import com.zyd.blog.business.entity.Resources;
import com.zyd.blog.business.entity.Role;
import com.zyd.blog.business.entity.User;
import com.zyd.blog.business.enums.UserStatusEnum;
import com.zyd.blog.business.enums.UserTypeEnum;
import com.zyd.blog.business.service.SysResourcesService;
import com.zyd.blog.business.service.SysRoleService;
import com.zyd.blog.business.service.SysUserService;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.*;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ByteSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShiroRealm 认证授权测试")
class ShiroRealmTest {

    @Mock
    private SysUserService userService;
    @Mock
    private SysResourcesService resourcesService;
    @Mock
    private SysRoleService roleService;

    private ShiroRealm shiroRealm;

    @BeforeEach
    void setUp() {
        shiroRealm = new ShiroRealm();
        ReflectionTestUtils.setField(shiroRealm, "userService", userService);
        ReflectionTestUtils.setField(shiroRealm, "resourcesService", resourcesService);
        ReflectionTestUtils.setField(shiroRealm, "roleService", roleService);
    }

    // ==================== doGetAuthenticationInfo 认证测试 ====================

    @Test
    @DisplayName("认证 - 账号不存在时抛出UnknownAccountException")
    void authentication_unknownAccount_throwsException() {
        UsernamePasswordToken token = new UsernamePasswordToken("unknown", "password");
        when(userService.getByUserName("unknown")).thenReturn(null);

        assertThrows(UnknownAccountException.class, () -> shiroRealm.doGetAuthenticationInfo(token));
    }

    @Test
    @DisplayName("认证 - 账号被锁定时抛出LockedAccountException")
    void authentication_lockedAccount_throwsException() {
        User user = new User();
        user.setId(1L);
        user.setUsername("locked");
        user.setPassword("encrypted");
        user.setStatus(UserStatusEnum.DISABLE.getCode());

        UsernamePasswordToken token = new UsernamePasswordToken("locked", "password");
        when(userService.getByUserName("locked")).thenReturn(user);

        assertThrows(LockedAccountException.class, () -> shiroRealm.doGetAuthenticationInfo(token));
    }

    @Test
    @DisplayName("认证 - 正常账号返回正确的认证信息")
    void authentication_normalAccount_returnsAuthInfo() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("encryptedPassword");
        user.setStatus(UserStatusEnum.NORMAL.getCode());

        UsernamePasswordToken token = new UsernamePasswordToken("admin", "password");
        when(userService.getByUserName("admin")).thenReturn(user);

        AuthenticationInfo info = shiroRealm.doGetAuthenticationInfo(token);

        assertNotNull(info);
        assertEquals(1L, info.getPrincipals().getPrimaryPrincipal());
        assertEquals("encryptedPassword", info.getCredentials());
        // salt 应该是用户名的字节
        ByteSource expectedSalt = ByteSource.Util.bytes("admin");
        assertTrue(info instanceof org.apache.shiro.authc.SaltedAuthenticationInfo);
        assertEquals(expectedSalt, ((org.apache.shiro.authc.SaltedAuthenticationInfo) info).getCredentialsSalt());
    }

    @Test
    @DisplayName("认证 - 正常账号status为null时不抛异常")
    void authentication_nullStatus_returnsAuthInfo() {
        User user = new User();
        user.setId(2L);
        user.setUsername("user1");
        user.setPassword("pwd");
        user.setStatus(null);

        UsernamePasswordToken token = new UsernamePasswordToken("user1", "password");
        when(userService.getByUserName("user1")).thenReturn(user);

        AuthenticationInfo info = shiroRealm.doGetAuthenticationInfo(token);

        assertNotNull(info);
        assertEquals(2L, info.getPrincipals().getPrimaryPrincipal());
    }

    // ==================== doGetAuthorizationInfo 授权测试 ====================

    @Test
    @DisplayName("授权 - ROOT用户拥有所有权限")
    void authorization_rootUser_hasAllPermissions() {
        User rootUser = new User();
        rootUser.setId(1L);
        rootUser.setUserType(UserTypeEnum.ROOT);

        Role adminRole = new Role();
        adminRole.setName("admin");

        Resources resource1 = new Resources();
        resource1.setPermission("article:list");
        Resources resource2 = new Resources();
        resource2.setPermission("article:add,article:edit");

        when(roleService.listRolesByUserId(1L)).thenReturn(Collections.singletonList(adminRole));
        when(userService.getByPrimaryKey(1L)).thenReturn(rootUser);
        when(resourcesService.listAll()).thenReturn(Arrays.asList(resource1, resource2));

        try (MockedStatic<SecurityUtils> securityMock = mockStatic(SecurityUtils.class)) {
            Subject mockSubject = mock(Subject.class);
            when(mockSubject.getPrincipal()).thenReturn(1L);
            securityMock.when(SecurityUtils::getSubject).thenReturn(mockSubject);

            AuthorizationInfo info = shiroRealm.doGetAuthorizationInfo(null);

            assertNotNull(info);
            assertTrue(info.getRoles().contains("admin"));
            assertTrue(info.getStringPermissions().contains("article:list"));
            assertTrue(info.getStringPermissions().contains("article:add"));
            assertTrue(info.getStringPermissions().contains("article:edit"));
            assertEquals(3, info.getStringPermissions().size());
        }
    }

    @Test
    @DisplayName("授权 - 普通用户仅拥有分配的权限")
    void authorization_normalUser_hasAssignedPermissions() {
        User normalUser = new User();
        normalUser.setId(2L);
        normalUser.setUserType(UserTypeEnum.USER);

        Role userRole = new Role();
        userRole.setName("user");

        Resources resource = new Resources();
        resource.setPermission("article:view");

        when(roleService.listRolesByUserId(2L)).thenReturn(Collections.singletonList(userRole));
        when(userService.getByPrimaryKey(2L)).thenReturn(normalUser);
        when(resourcesService.listByUserId(2L)).thenReturn(Collections.singletonList(resource));

        try (MockedStatic<SecurityUtils> securityMock = mockStatic(SecurityUtils.class)) {
            Subject mockSubject = mock(Subject.class);
            when(mockSubject.getPrincipal()).thenReturn(2L);
            securityMock.when(SecurityUtils::getSubject).thenReturn(mockSubject);

            AuthorizationInfo info = shiroRealm.doGetAuthorizationInfo(null);

            assertNotNull(info);
            assertTrue(info.getRoles().contains("user"));
            assertTrue(info.getStringPermissions().contains("article:view"));
            assertEquals(1, info.getStringPermissions().size());
        }
    }

    @Test
    @DisplayName("授权 - 用户不存在时返回空权限")
    void authorization_userNotFound_returnsEmptyInfo() {
        when(roleService.listRolesByUserId(999L)).thenReturn(null);
        when(userService.getByPrimaryKey(999L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> securityMock = mockStatic(SecurityUtils.class)) {
            Subject mockSubject = mock(Subject.class);
            when(mockSubject.getPrincipal()).thenReturn(999L);
            securityMock.when(SecurityUtils::getSubject).thenReturn(mockSubject);

            AuthorizationInfo info = shiroRealm.doGetAuthorizationInfo(null);

            assertNotNull(info);
            assertTrue(info.getRoles() == null || info.getRoles().isEmpty());
        }
    }

    @Test
    @DisplayName("授权 - 用户无角色时返回空角色集合")
    void authorization_noRoles_returnsEmptyRoles() {
        User user = new User();
        user.setId(3L);
        user.setUserType(UserTypeEnum.USER);

        when(roleService.listRolesByUserId(3L)).thenReturn(null);
        when(userService.getByPrimaryKey(3L)).thenReturn(user);
        when(resourcesService.listByUserId(3L)).thenReturn(null);

        try (MockedStatic<SecurityUtils> securityMock = mockStatic(SecurityUtils.class)) {
            Subject mockSubject = mock(Subject.class);
            when(mockSubject.getPrincipal()).thenReturn(3L);
            securityMock.when(SecurityUtils::getSubject).thenReturn(mockSubject);

            AuthorizationInfo info = shiroRealm.doGetAuthorizationInfo(null);

            assertNotNull(info);
            assertTrue(info.getRoles() == null || info.getRoles().isEmpty());
        }
    }

    @Test
    @DisplayName("授权 - 资源权限为空字符串时不添加权限")
    void authorization_emptyPermission_skipped() {
        User user = new User();
        user.setId(4L);
        user.setUserType(UserTypeEnum.USER);

        Resources resource = new Resources();
        resource.setPermission("");

        when(roleService.listRolesByUserId(4L)).thenReturn(null);
        when(userService.getByPrimaryKey(4L)).thenReturn(user);
        when(resourcesService.listByUserId(4L)).thenReturn(Collections.singletonList(resource));

        try (MockedStatic<SecurityUtils> securityMock = mockStatic(SecurityUtils.class)) {
            Subject mockSubject = mock(Subject.class);
            when(mockSubject.getPrincipal()).thenReturn(4L);
            securityMock.when(SecurityUtils::getSubject).thenReturn(mockSubject);

            AuthorizationInfo info = shiroRealm.doGetAuthorizationInfo(null);

            assertNotNull(info);
            assertTrue(info.getStringPermissions() == null || info.getStringPermissions().isEmpty());
        }
    }
}
