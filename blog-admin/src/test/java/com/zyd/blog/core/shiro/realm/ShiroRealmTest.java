package com.zyd.blog.core.shiro.realm;

import com.zyd.blog.business.entity.Resources;
import com.zyd.blog.business.entity.Role;
import com.zyd.blog.business.entity.User;
import com.zyd.blog.business.enums.UserStatusEnum;
import com.zyd.blog.business.service.SysResourcesService;
import com.zyd.blog.business.service.SysRoleService;
import com.zyd.blog.business.service.SysUserService;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.UnknownAccountException;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ShiroRealm 认证（doGetAuthenticationInfo）与授权（doGetAuthorizationInfo）单元测试。
 * 覆盖正常登录、账号不存在、账号被锁定，以及 ROOT/普通用户/用户不存在的授权场景。
 * 通过 ThreadContext 绑定一个 mock 的 Subject 来满足 SecurityUtils.getSubject() 的调用。
 */
@ExtendWith(MockitoExtension.class)
class ShiroRealmTest {

    @Mock
    private SysUserService userService;
    @Mock
    private SysResourcesService resourcesService;
    @Mock
    private SysRoleService roleService;

    @InjectMocks
    private ShiroRealm shiroRealm;

    @AfterEach
    void clearSubject() {
        ThreadContext.remove();
    }

    private void bindSubject(Long principal) {
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(principal);
        ThreadContext.bind(subject);
    }

    // ---------- 认证 ----------

    @Test
    void doGetAuthenticationInfo_success_returnsInfoWithUserIdAndPassword() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("encryptedPwd");
        user.setStatus(UserStatusEnum.NORMAL.getCode());
        when(userService.getByUserName("admin")).thenReturn(user);

        AuthenticationInfo info = shiroRealm.doGetAuthenticationInfo(
                new UsernamePasswordToken("admin", "raw".toCharArray()));

        assertNotNull(info);
        assertEquals(Long.valueOf(1L), info.getPrincipals().getPrimaryPrincipal());
        assertEquals("encryptedPwd", info.getCredentials());
    }

    @Test
    void doGetAuthenticationInfo_throwsUnknownAccount_whenUserMissing() {
        when(userService.getByUserName("ghost")).thenReturn(null);

        assertThrows(UnknownAccountException.class, () -> shiroRealm.doGetAuthenticationInfo(
                new UsernamePasswordToken("ghost", "raw".toCharArray())));
    }

    @Test
    void doGetAuthenticationInfo_throwsLockedAccount_whenUserDisabled() {
        User user = new User();
        user.setId(2L);
        user.setUsername("bob");
        user.setStatus(UserStatusEnum.DISABLE.getCode());
        when(userService.getByUserName("bob")).thenReturn(user);

        assertThrows(LockedAccountException.class, () -> shiroRealm.doGetAuthenticationInfo(
                new UsernamePasswordToken("bob", "raw".toCharArray())));
    }

    // ---------- 授权 ----------

    @Test
    void doGetAuthorizationInfo_rootUser_getsAllResourcesAsPermissions() {
        bindSubject(1L);
        Role role = new Role();
        role.setName("admin");
        when(roleService.listRolesByUserId(1L)).thenReturn(Arrays.asList(role));

        User user = new User();
        user.setId(1L);
        user.setUserType("ROOT");
        when(userService.getByPrimaryKey(1L)).thenReturn(user);

        Resources res = new Resources();
        res.setPermission("article:list,article:edit");
        when(resourcesService.listAll()).thenReturn(Arrays.asList(res));

        AuthorizationInfo info = shiroRealm.doGetAuthorizationInfo(new SimplePrincipalCollection(1L, "shiroRealm"));

        assertNotNull(info);
        assertTrue(info.getRoles().contains("admin"));
        assertTrue(info.getStringPermissions().contains("article:list"));
        assertTrue(info.getStringPermissions().contains("article:edit"));
    }

    @Test
    void doGetAuthorizationInfo_normalUser_getsResourcesByUserId() {
        bindSubject(2L);
        Role role = new Role();
        role.setName("editor");
        when(roleService.listRolesByUserId(2L)).thenReturn(Arrays.asList(role));

        User user = new User();
        user.setId(2L);
        user.setUserType("USER");
        when(userService.getByPrimaryKey(2L)).thenReturn(user);

        Resources res = new Resources();
        res.setPermission("comment:list");
        when(resourcesService.listByUserId(2L)).thenReturn(Arrays.asList(res));

        AuthorizationInfo info = shiroRealm.doGetAuthorizationInfo(new SimplePrincipalCollection(2L, "shiroRealm"));

        assertNotNull(info);
        assertTrue(info.getRoles().contains("editor"));
        assertTrue(info.getStringPermissions().contains("comment:list"));
    }

    @Test
    void doGetAuthorizationInfo_userMissing_returnsRolesWithoutPermissions() {
        bindSubject(3L);
        Role role = new Role();
        role.setName("guest");
        when(roleService.listRolesByUserId(3L)).thenReturn(Arrays.asList(role));
        when(userService.getByPrimaryKey(3L)).thenReturn(null);

        AuthorizationInfo info = shiroRealm.doGetAuthorizationInfo(new SimplePrincipalCollection(3L, "shiroRealm"));

        assertNotNull(info);
        assertTrue(info.getRoles().contains("guest"));
        assertTrue(info.getStringPermissions() == null || info.getStringPermissions().isEmpty());
    }

    @Test
    void doGetAuthorizationInfo_noRoles_doesNotFail() {
        bindSubject(4L);
        when(roleService.listRolesByUserId(4L)).thenReturn(Collections.emptyList());
        User user = new User();
        user.setId(4L);
        user.setUserType("USER");
        when(userService.getByPrimaryKey(4L)).thenReturn(user);
        when(resourcesService.listByUserId(4L)).thenReturn(Collections.emptyList());

        AuthorizationInfo info = shiroRealm.doGetAuthorizationInfo(new SimplePrincipalCollection(4L, "shiroRealm"));

        assertNotNull(info);
        assertTrue(info.getRoles() == null || info.getRoles().isEmpty());
    }
}
