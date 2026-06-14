package com.zyd.blog.business.service.impl;

import com.zyd.blog.business.entity.Article;
import com.zyd.blog.business.service.BizArticleContentService;
import com.zyd.blog.business.service.BizArticleTagsService;
import com.zyd.blog.framework.exception.ZhydArticleException;
import com.zyd.blog.framework.exception.ZhydException;
import com.zyd.blog.persistence.beans.BizArticle;
import com.zyd.blog.persistence.beans.BizArticleLove;
import com.zyd.blog.persistence.beans.BizArticleLookV2;
import com.zyd.blog.persistence.beans.BizArticleTags;
import com.zyd.blog.persistence.beans.BizComment;
import com.zyd.blog.persistence.mapper.BizArticleLoveMapper;
import com.zyd.blog.persistence.mapper.BizArticleLookV2Mapper;
import com.zyd.blog.persistence.mapper.BizArticleMapper;
import com.zyd.blog.persistence.mapper.BizArticleTagsMapper;
import com.zyd.blog.persistence.mapper.BizCommentMapper;
import com.zyd.blog.persistence.mapper.BizStatisticsMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import tk.mybatis.mapper.entity.Config;
import tk.mybatis.mapper.mapperhelper.EntityHelper;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BizArticleServiceImpl 单元测试。
 * 使用 Mockito 隔离持久层（Mapper）与依赖服务，无需启动 Spring 容器或数据库。
 */
@ExtendWith(MockitoExtension.class)
class BizArticleServiceImplTest {

    @Mock
    private BizArticleMapper bizArticleMapper;
    @Mock
    private BizArticleLoveMapper bizArticleLoveMapper;
    @Mock
    private BizArticleLookV2Mapper bizArticleLookV2Mapper;
    @Mock
    private BizArticleTagsMapper bizArticleTagsMapper;
    @Mock
    private RedisTemplate redisTemplate;
    @Mock
    private BizArticleTagsService articleTagsService;
    @Mock
    private BizCommentMapper commentMapper;
    @Mock
    private BizArticleContentService articleContentService;
    @Mock
    private BizStatisticsMapper bizStatisticsMapper;

    @InjectMocks
    private BizArticleServiceImpl bizArticleService;

    /**
     * 注册 tk.mybatis 实体表信息。getByPrimaryKey/removeByPrimaryKey 内部会构造 Example，
     * Example 构造时需通过 EntityHelper 解析实体对应的表结构；纯单元测试未启动 Spring/MyBatis，
     * 故在此手动注册，使被测代码中的 Example 构造正常执行（Mapper 方法本身仍为 Mock）。
     */
    @BeforeAll
    static void registerMyBatisEntities() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(BizArticleTags.class, config);
        EntityHelper.initEntityNameMap(BizArticleLookV2.class, config);
        EntityHelper.initEntityNameMap(BizArticleLove.class, config);
        EntityHelper.initEntityNameMap(BizComment.class, config);
    }

    @Test
    void isExist_returnsTrue_whenCountPositive() {
        when(bizArticleMapper.isExist(1L)).thenReturn(1);

        assertTrue(bizArticleService.isExist(1L));
    }

    @Test
    void isExist_returnsFalse_whenCountZero() {
        when(bizArticleMapper.isExist(2L)).thenReturn(0);

        assertFalse(bizArticleService.isExist(2L));
    }

    @Test
    void isExist_returnsFalse_whenCountNull() {
        when(bizArticleMapper.isExist(3L)).thenReturn(null);

        assertFalse(bizArticleService.isExist(3L));
    }

    @Test
    void publish_throws_whenTagsEmpty() {
        Article article = new Article();

        assertThrows(ZhydArticleException.class, () -> bizArticleService.publish(article, new Long[]{}, null));
        verifyNoInteractions(bizArticleMapper);
    }

    @Test
    void publish_throws_whenTagsNull() {
        Article article = new Article();

        assertThrows(ZhydArticleException.class, () -> bizArticleService.publish(article, null, null));
        verifyNoInteractions(bizArticleMapper);
    }

    @Test
    void updateTopOrRecommendedById_top_togglesValueAndUpdates() {
        BizArticle biz = new BizArticle();
        biz.setTop(false);
        when(bizArticleMapper.selectByPrimaryKey(1L)).thenReturn(biz);
        when(bizArticleMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        boolean result = bizArticleService.updateTopOrRecommendedById("top", 1L);

        assertTrue(result);
        assertTrue(biz.getTop());
        verify(bizArticleMapper).updateByPrimaryKeySelective(biz);
    }

    @Test
    void updateTopOrRecommendedById_recommend_togglesValueAndUpdates() {
        BizArticle biz = new BizArticle();
        biz.setRecommended(false);
        when(bizArticleMapper.selectByPrimaryKey(1L)).thenReturn(biz);
        when(bizArticleMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        boolean result = bizArticleService.updateTopOrRecommendedById("recommend", 1L);

        assertTrue(result);
        assertTrue(biz.getRecommended());
    }

    @Test
    void updateTopOrRecommendedById_invalidType_throwsAndDoesNotUpdate() {
        BizArticle biz = new BizArticle();
        when(bizArticleMapper.selectByPrimaryKey(1L)).thenReturn(biz);

        assertThrows(ZhydException.class, () -> bizArticleService.updateTopOrRecommendedById("unknown", 1L));
        verify(bizArticleMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void batchUpdateStatus_doesNothing_whenIdsEmpty() {
        bizArticleService.batchUpdateStatus(new Long[]{}, true);
        bizArticleService.batchUpdateStatus(null, true);

        verify(bizArticleMapper, never()).batchUpdateStatus(anyList(), anyBoolean());
    }

    @Test
    void batchUpdateStatus_callsMapper_whenIdsPresent() {
        bizArticleService.batchUpdateStatus(new Long[]{1L, 2L}, true);

        verify(bizArticleMapper).batchUpdateStatus(Arrays.asList(1L, 2L), true);
    }

    @Test
    void getByPrimaryKey_throws_whenKeyNull() {
        assertThrows(IllegalArgumentException.class, () -> bizArticleService.getByPrimaryKey(null));
    }

    @Test
    void getByPrimaryKey_returnsNull_whenNotFound() {
        when(bizArticleMapper.get(9L)).thenReturn(null);

        assertNull(bizArticleService.getByPrimaryKey(9L));
    }

    @Test
    void getByPrimaryKey_returnsArticleWithSubqueryCounts_whenFound() {
        BizArticle biz = new BizArticle();
        biz.setId(5L);
        biz.setTitle("hello");
        when(bizArticleMapper.get(5L)).thenReturn(biz);
        when(bizArticleLookV2Mapper.selectOneByExample(any())).thenReturn(null);
        when(commentMapper.selectCountByExample(any())).thenReturn(0);
        when(bizArticleLoveMapper.selectCount(any())).thenReturn(0);

        Article article = bizArticleService.getByPrimaryKey(5L);

        assertNotNull(article);
        assertEquals(Long.valueOf(5L), article.getId());
        assertEquals("hello", article.getTitle());
    }

    @Test
    void insert_throws_whenEntityNull() {
        assertThrows(IllegalArgumentException.class, () -> bizArticleService.insert(null));
    }

    @Test
    void insert_persistsArticleContentAndLookRecord() {
        Article article = new Article();
        article.setId(7L);
        article.setContent("content");
        article.setContentMd("contentMd");

        Article result = bizArticleService.insert(article);

        assertSame(article, result);
        verify(bizArticleMapper).insertSelective(article.getBizArticle());
        verify(articleContentService).insert(any());
        verify(bizArticleLookV2Mapper).insert(any());
    }

    @Test
    void removeByPrimaryKey_deletesArticleAndAllRelatedData() {
        when(bizArticleMapper.deleteByPrimaryKey(3L)).thenReturn(1);

        boolean result = bizArticleService.removeByPrimaryKey(3L);

        assertTrue(result);
        verify(articleContentService).removeByArticleId(3L);
        verify(bizArticleTagsMapper).deleteByExample(any());
        verify(bizArticleLookV2Mapper).deleteByExample(any());
        verify(bizArticleLoveMapper).deleteByExample(any());
    }
}
