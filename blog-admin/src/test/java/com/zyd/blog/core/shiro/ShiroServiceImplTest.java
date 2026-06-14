package com.zyd.blog.core.shiro;

import com.zyd.blog.business.entity.Resources;
import com.zyd.blog.business.entity.User;
import com.zyd.blog.business.service.SysResourcesService;
import com.zyd.blog.business.service.SysUserService;
import com.zyd.blog.framework.exception.ZhydException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShiroServiceImpl 权限服务测试")
class ShiroServiceImplTest {

    @Mock
    private SysResourcesService resourcesService;
    @Mock
    private SysUserService userService;

    @InjectMocks
    private ShiroServiceImpl shiroService;

    // ==================== loadFilterChainDefinitions ====================

    @Test
    @DisplayName("加载权限链 - 未加载到资源时抛出ZhydException")
    void loadFilterChainDefinitions_noResources_throwsException() {
        when(resourcesService.listUrlAndPermission()).thenReturn(new ArrayList<>());

        assertThrows(ZhydException.class, () -> shiroService.loadFilterChainDefinitions());
    }

    @Test
    @DisplayName("加载权限链 - 包含基础匿名路径和数据库资源权限")
    void loadFilterChainDefinitions_success() {
        Resources r1 = new Resources();
        r1.setUrl("/admin/articles");
        r1.setPermission("article:list");

        Resources r2 = new Resources();
        r2.setUrl("/admin/comments");
        r2.setPermission("comment:list");

        when(resourcesService.listUrlAndPermission()).thenReturn(Arrays.asList(r1, r2));

        Map<String, String> result = shiroService.loadFilterChainDefinitions();

        assertNotNull(result);
        // 验证匿名路径
        assertEquals("anon", result.get("/passport/login"));
        assertEquals("anon", result.get("/passport/signin"));
        assertEquals("anon", result.get("/favicon.ico"));
        assertEquals("anon", result.get("/error"));
        assertEquals("anon", result.get("/assets/**"));
        assertEquals("anon", result.get("/getKaptcha"));
        // 验证退出路径
        assertEquals("logout", result.get("/passport/logout"));
        // 验证数据库资源权限
        assertEquals("perms[article:list]", result.get("/admin/articles"));
        assertEquals("perms[comment:list]", result.get("/admin/comments"));
        // 验证兜底规则
        assertEquals("user", result.get("/**"));
    }

    @Test
    @DisplayName("加载权限链 - 资源URL或权限为空时跳过该资源")
    void loadFilterChainDefinitions_emptyUrlOrPermission_skipped() {
        Resources r1 = new Resources();
        r1.setUrl("");
        r1.setPermission("article:list");

        Resources r2 = new Resources();
        r2.setUrl("/admin/users");
        r2.setPermission("");

        Resources r3 = new Resources();
        r3.setUrl("/admin/articles");
        r3.setPermission("article:list");

        when(resourcesService.listUrlAndPermission()).thenReturn(Arrays.asList(r1, r2, r3));

        Map<String, String> result = shiroService.loadFilterChainDefinitions();

        // r1 和 r2 应该被跳过
        assertNull(result.get(""));
        assertNull(result.get("/admin/users"));
        // r3 应该被添加
        assertEquals("perms[article:list]", result.get("/admin/articles"));
    }

    // ==================== reloadAuthorizingByRoleId ====================

    @Test
    @DisplayName("重载角色权限 - 角色下无用户时不执行操作")
    void reloadAuthorizingByRoleId_noUsers_noOp() {
        when(userService.listByRoleId(1L)).thenReturn(null);

        shiroService.reloadAuthorizingByRoleId(1L);

        verify(userService).listByRoleId(1L);
    }

    @Test
    @DisplayName("重载角色权限 - 角色下用户列表为空时不执行操作")
    void reloadAuthorizingByRoleId_emptyUsers_noOp() {
        when(userService.listByRoleId(1L)).thenReturn(new ArrayList<>());

        shiroService.reloadAuthorizingByRoleId(1L);

        verify(userService).listByRoleId(1L);
    }
}
