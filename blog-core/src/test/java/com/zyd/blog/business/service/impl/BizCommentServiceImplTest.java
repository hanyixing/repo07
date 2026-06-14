package com.zyd.blog.business.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.zyd.blog.business.entity.Comment;
import com.zyd.blog.business.service.MailService;
import com.zyd.blog.business.service.SysConfigService;
import com.zyd.blog.business.vo.CommentConditionVO;
import com.zyd.blog.framework.exception.ZhydCommentException;
import com.zyd.blog.persistence.beans.BizComment;
import com.zyd.blog.persistence.mapper.BizCommentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BizCommentServiceImpl 单元测试。
 * 覆盖评论的增删改查以及评论发表（审核）流程中的异常场景，均使用 Mockito 隔离依赖。
 */
@ExtendWith(MockitoExtension.class)
class BizCommentServiceImplTest {

    @Mock
    private BizCommentMapper bizCommentMapper;
    @Mock
    private RedisTemplate redisTemplate;
    @Mock
    private MailService mailService;
    @Mock
    private SysConfigService configService;

    @InjectMocks
    private BizCommentServiceImpl bizCommentService;

    @AfterEach
    void clearPageHelper() {
        // findPageBreakByCondition 调用了 PageHelper.startPage，Mapper 被 mock 后不会消费 ThreadLocal，这里主动清理避免泄漏。
        PageHelper.clearPage();
    }

    @Test
    void insert_throws_whenEntityNull() {
        assertThrows(IllegalArgumentException.class, () -> bizCommentService.insert(null));
    }

    @Test
    void insert_savesComment() {
        Comment comment = new Comment();
        comment.setNickname("tester");

        Comment result = bizCommentService.insert(comment);

        assertSame(comment, result);
        verify(bizCommentMapper).insertSelective(comment.getBizComment());
    }

    @Test
    void removeByPrimaryKey_returnsTrue_whenDeleted() {
        when(bizCommentMapper.deleteByPrimaryKey(1L)).thenReturn(1);

        assertTrue(bizCommentService.removeByPrimaryKey(1L));
    }

    @Test
    void updateSelective_throws_whenEntityNull() {
        assertThrows(IllegalArgumentException.class, () -> bizCommentService.updateSelective(null));
    }

    @Test
    void updateSelective_returnsTrue_whenUpdated() {
        Comment comment = new Comment();
        comment.setId(1L);
        when(bizCommentMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        assertTrue(bizCommentService.updateSelective(comment));
        verify(bizCommentMapper).updateByPrimaryKeySelective(comment.getBizComment());
    }

    @Test
    void getByPrimaryKey_throws_whenKeyNull() {
        assertThrows(IllegalArgumentException.class, () -> bizCommentService.getByPrimaryKey(null));
    }

    @Test
    void getByPrimaryKey_returnsNull_whenNotFound() {
        when(bizCommentMapper.getById(9L)).thenReturn(null);

        assertNull(bizCommentService.getByPrimaryKey(9L));
    }

    @Test
    void getByPrimaryKey_returnsComment_whenFound() {
        BizComment biz = new BizComment();
        biz.setId(2L);
        when(bizCommentMapper.getById(2L)).thenReturn(biz);

        Comment comment = bizCommentService.getByPrimaryKey(2L);

        assertNotNull(comment);
        assertEquals(2L, comment.getId());
    }

    @Test
    void findPageBreakByCondition_returnsNull_whenNoData() {
        when(bizCommentMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        assertNull(bizCommentService.findPageBreakByCondition(new CommentConditionVO()));
    }

    @Test
    void findPageBreakByCondition_returnsPage_whenDataPresent() {
        List<BizComment> data = new ArrayList<>();
        data.add(new BizComment());
        when(bizCommentMapper.findPageBreakByCondition(any())).thenReturn(data);

        PageInfo<Comment> page = bizCommentService.findPageBreakByCondition(new CommentConditionVO());

        assertNotNull(page);
        assertEquals(1, page.getList().size());
    }

    @Test
    void list_returnsEmptyMap_whenNoData() {
        when(bizCommentMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        Map<String, Object> result = bizCommentService.list(new CommentConditionVO());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void listVerifying_returnsNull_whenNoData() {
        when(bizCommentMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        assertNull(bizCommentService.listVerifying(10));
    }

    @Test
    void comment_throws_whenContentEmpty() {
        // 站长开启匿名评论（getByKey 返回 null 时默认匿名），随后内容为空应被拦截。
        when(configService.getByKey(any())).thenReturn(null);
        Comment comment = new Comment();

        assertThrows(ZhydCommentException.class, () -> bizCommentService.comment(comment));
    }
}
