package com.zyd.blog.business.service.impl;

import com.zyd.blog.business.entity.Comment;
import com.zyd.blog.business.enums.CommentStatusEnum;
import com.zyd.blog.business.service.MailService;
import com.zyd.blog.business.service.SysConfigService;
import com.zyd.blog.business.vo.CommentConditionVO;
import com.zyd.blog.framework.exception.ZhydCommentException;
import com.zyd.blog.framework.holder.RequestHolder;
import com.zyd.blog.persistence.beans.BizComment;
import com.zyd.blog.persistence.mapper.BizCommentMapper;
import com.zyd.blog.util.IpUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BizCommentServiceImpl 单元测试")
class BizCommentServiceImplTest {

    @Mock
    private BizCommentMapper bizCommentMapper;
    @Mock
    private RedisTemplate redisTemplate;
    @Mock
    private MailService mailService;
    @Mock
    private SysConfigService configService;
    @Mock
    private ValueOperations valueOperations;

    @InjectMocks
    private BizCommentServiceImpl commentService;

    // ==================== getByPrimaryKey ====================

    @Test
    @DisplayName("getByPrimaryKey - primaryKey为null时抛出异常")
    void getByPrimaryKey_nullKey_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> commentService.getByPrimaryKey(null));
    }

    @Test
    @DisplayName("getByPrimaryKey - 评论不存在时返回null")
    void getByPrimaryKey_notExist_returnsNull() {
        when(bizCommentMapper.getById(999L)).thenReturn(null);
        assertNull(commentService.getByPrimaryKey(999L));
    }

    @Test
    @DisplayName("getByPrimaryKey - 正常返回评论")
    void getByPrimaryKey_success() {
        BizComment bizComment = new BizComment();
        bizComment.setId(1L);
        bizComment.setContent("Hello");
        bizComment.setNickname("tester");

        when(bizCommentMapper.getById(1L)).thenReturn(bizComment);

        Comment result = commentService.getByPrimaryKey(1L);

        assertNotNull(result);
        assertEquals("Hello", result.getContent());
        assertEquals("tester", result.getNickname());
    }

    // ==================== insert ====================

    @Test
    @DisplayName("insert - entity为null时抛出异常")
    void insert_nullEntity_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> commentService.insert(null));
    }

    @Test
    @DisplayName("insert - 正常插入评论")
    void insert_success() {
        Comment comment = new Comment();
        comment.setContent("Nice article!");
        comment.setNickname("tester");

        when(bizCommentMapper.insertSelective(any(BizComment.class))).thenReturn(1);

        Comment result = commentService.insert(comment);

        assertNotNull(result);
        assertNotNull(result.getCreateTime());
        assertNotNull(result.getUpdateTime());
        verify(bizCommentMapper).insertSelective(any(BizComment.class));
    }

    // ==================== removeByPrimaryKey ====================

    @Test
    @DisplayName("removeByPrimaryKey - 删除成功返回true")
    void removeByPrimaryKey_success() {
        when(bizCommentMapper.deleteByPrimaryKey(1L)).thenReturn(1);

        boolean result = commentService.removeByPrimaryKey(1L);
        assertTrue(result);
    }

    @Test
    @DisplayName("removeByPrimaryKey - 评论不存在时返回false")
    void removeByPrimaryKey_notExist_returnsFalse() {
        when(bizCommentMapper.deleteByPrimaryKey(999L)).thenReturn(0);

        boolean result = commentService.removeByPrimaryKey(999L);
        assertFalse(result);
    }

    // ==================== updateSelective ====================

    @Test
    @DisplayName("updateSelective - entity为null时抛出异常")
    void updateSelective_nullEntity_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> commentService.updateSelective(null));
    }

    @Test
    @DisplayName("updateSelective - 更新成功返回true")
    void updateSelective_success() {
        Comment comment = new Comment();
        comment.setId(1L);
        comment.setStatus(CommentStatusEnum.APPROVED.toString());

        when(bizCommentMapper.updateByPrimaryKeySelective(any(BizComment.class))).thenReturn(1);

        boolean result = commentService.updateSelective(comment);

        assertTrue(result);
        assertNotNull(comment.getUpdateTime());
    }

    @Test
    @DisplayName("updateSelective - 更新失败返回false")
    void updateSelective_fails_returnsFalse() {
        Comment comment = new Comment();
        comment.setId(1L);

        when(bizCommentMapper.updateByPrimaryKeySelective(any(BizComment.class))).thenReturn(0);

        boolean result = commentService.updateSelective(comment);
        assertFalse(result);
    }

    // ==================== findPageBreakByCondition ====================

    @Test
    @DisplayName("findPageBreakByCondition - 无数据时返回null")
    void findPageBreakByCondition_empty_returnsNull() {
        CommentConditionVO vo = new CommentConditionVO();
        vo.setPageNumber(1);
        vo.setPageSize(10);

        when(bizCommentMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        assertNull(commentService.findPageBreakByCondition(vo));
    }

    @Test
    @DisplayName("findPageBreakByCondition - 有数据时返回分页结果")
    void findPageBreakByCondition_withData_returnsPageInfo() {
        CommentConditionVO vo = new CommentConditionVO();
        vo.setPageNumber(1);
        vo.setPageSize(10);

        BizComment bc = new BizComment();
        bc.setId(1L);
        bc.setContent("Great post!");
        bc.setNickname("user1");

        when(bizCommentMapper.findPageBreakByCondition(any())).thenReturn(Collections.singletonList(bc));

        com.github.pagehelper.PageInfo<Comment> result = commentService.findPageBreakByCondition(vo);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
    }

    // ==================== list (评论列表Map) ====================

    @Test
    @DisplayName("list - 无数据时返回空Map")
    void list_empty_returnsEmptyMap() {
        CommentConditionVO vo = new CommentConditionVO();
        vo.setPageNumber(1);
        vo.setPageSize(10);

        when(bizCommentMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        Map<String, Object> result = commentService.list(vo);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== listRecentComment ====================

    @Test
    @DisplayName("listRecentComment - 无数据时返回null")
    void listRecentComment_empty_returnsNull() {
        when(bizCommentMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        List<Comment> result = commentService.listRecentComment(10);
        assertNull(result);
    }

    @Test
    @DisplayName("listRecentComment - 有数据时返回近期评论列表")
    void listRecentComment_withData_returnsList() {
        BizComment bc = new BizComment();
        bc.setId(1L);
        bc.setContent("Recent comment");
        bc.setStatus(CommentStatusEnum.APPROVED.toString());

        when(bizCommentMapper.findPageBreakByCondition(any())).thenReturn(Collections.singletonList(bc));

        List<Comment> result = commentService.listRecentComment(10);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    // ==================== listVerifying ====================

    @Test
    @DisplayName("listVerifying - 无数据时返回null")
    void listVerifying_empty_returnsNull() {
        when(bizCommentMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        List<Comment> result = commentService.listVerifying(10);
        assertNull(result);
    }

    @Test
    @DisplayName("listVerifying - 有数据时返回未审核评论列表")
    void listVerifying_withData_returnsList() {
        BizComment bc = new BizComment();
        bc.setId(1L);
        bc.setStatus(CommentStatusEnum.VERIFYING.toString());

        when(bizCommentMapper.findPageBreakByCondition(any())).thenReturn(Collections.singletonList(bc));

        List<Comment> result = commentService.listVerifying(10);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    // ==================== doSupport ====================

    @Test
    @DisplayName("doSupport - 已点过赞时抛出异常")
    void doSupport_alreadySupported_throwsException() {
        when(redisTemplate.hasKey(any())).thenReturn(true);

        try (MockedStatic<RequestHolder> rhMock = mockStatic(RequestHolder.class);
             MockedStatic<IpUtil> ipMock = mockStatic(IpUtil.class)) {

            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            rhMock.when(RequestHolder::getRequest).thenReturn(mockRequest);
            ipMock.when(() -> IpUtil.getRealIp(any())).thenReturn("127.0.0.1");

            assertThrows(ZhydCommentException.class, () -> commentService.doSupport(1L));
        }
    }

    @Test
    @DisplayName("doSupport - 首次点赞成功")
    void doSupport_success() {
        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        try (MockedStatic<RequestHolder> rhMock = mockStatic(RequestHolder.class);
             MockedStatic<IpUtil> ipMock = mockStatic(IpUtil.class)) {

            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            rhMock.when(RequestHolder::getRequest).thenReturn(mockRequest);
            ipMock.when(() -> IpUtil.getRealIp(any())).thenReturn("127.0.0.1");

            commentService.doSupport(1L);

            verify(bizCommentMapper).doSupport(1L);
            verify(valueOperations).set(any(), eq(1L), eq(1L), any());
        }
    }

    // ==================== doOppose ====================

    @Test
    @DisplayName("doOppose - 已点过踩时抛出异常")
    void doOppose_alreadyOpposed_throwsException() {
        when(redisTemplate.hasKey(any())).thenReturn(true);

        try (MockedStatic<RequestHolder> rhMock = mockStatic(RequestHolder.class);
             MockedStatic<IpUtil> ipMock = mockStatic(IpUtil.class)) {

            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            rhMock.when(RequestHolder::getRequest).thenReturn(mockRequest);
            ipMock.when(() -> IpUtil.getRealIp(any())).thenReturn("127.0.0.1");

            assertThrows(ZhydCommentException.class, () -> commentService.doOppose(1L));
        }
    }

    @Test
    @DisplayName("doOppose - 首次点踩成功")
    void doOppose_success() {
        when(redisTemplate.hasKey(any())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        try (MockedStatic<RequestHolder> rhMock = mockStatic(RequestHolder.class);
             MockedStatic<IpUtil> ipMock = mockStatic(IpUtil.class)) {

            HttpServletRequest mockRequest = mock(HttpServletRequest.class);
            rhMock.when(RequestHolder::getRequest).thenReturn(mockRequest);
            ipMock.when(() -> IpUtil.getRealIp(any())).thenReturn("127.0.0.1");

            commentService.doOppose(1L);

            verify(bizCommentMapper).doOppose(1L);
            verify(valueOperations).set(any(), eq(1L), eq(1L), any());
        }
    }
}
