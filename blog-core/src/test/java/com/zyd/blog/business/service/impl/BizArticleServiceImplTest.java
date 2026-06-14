package com.zyd.blog.business.service.impl;

import com.github.pagehelper.PageInfo;
import com.zyd.blog.business.entity.Article;
import com.zyd.blog.business.entity.BizArticleContentBo;
import com.zyd.blog.business.enums.ArticleStatusEnum;
import com.zyd.blog.business.service.BizArticleContentService;
import com.zyd.blog.business.service.BizArticleTagsService;
import com.zyd.blog.business.vo.ArticleConditionVO;
import com.zyd.blog.framework.exception.ZhydArticleException;
import com.zyd.blog.framework.exception.ZhydException;
import com.zyd.blog.persistence.beans.*;
import com.zyd.blog.persistence.mapper.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import tk.mybatis.mapper.entity.Example;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BizArticleServiceImpl 单元测试")
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
    private BizArticleServiceImpl articleService;

    // ==================== isExist ====================

    @Test
    @DisplayName("isExist - 文章不存在时返回false (count为null)")
    void isExist_whenCountIsNull_returnsFalse() {
        when(bizArticleMapper.isExist(1L)).thenReturn(null);
        assertFalse(articleService.isExist(1L));
    }

    @Test
    @DisplayName("isExist - 文章不存在时返回false (count为0)")
    void isExist_whenCountIsZero_returnsFalse() {
        when(bizArticleMapper.isExist(1L)).thenReturn(0);
        assertFalse(articleService.isExist(1L));
    }

    @Test
    @DisplayName("isExist - 文章存在时返回true")
    void isExist_whenCountIsPositive_returnsTrue() {
        when(bizArticleMapper.isExist(1L)).thenReturn(1);
        assertTrue(articleService.isExist(1L));
    }

    // ==================== insert ====================

    @Test
    @DisplayName("insert - entity为null时抛出异常")
    void insert_whenEntityIsNull_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> articleService.insert(null));
    }

    @Test
    @DisplayName("insert - 正常插入文章，同时插入内容和浏览记录")
    void insert_success() {
        Article article = new Article();
        article.setTitle("Test Article");
        article.setContent("<p>content</p>");
        article.setContentMd("# content");

        when(bizArticleMapper.insertSelective(any(BizArticle.class))).thenReturn(1);
        when(articleContentService.insert(any(BizArticleContentBo.class))).thenReturn(null);
        when(bizArticleLookV2Mapper.insert(any(BizArticleLookV2.class))).thenReturn(1);

        Article result = articleService.insert(article);

        assertNotNull(result);
        verify(bizArticleMapper).insertSelective(any(BizArticle.class));
        verify(articleContentService).insert(any(BizArticleContentBo.class));
        verify(bizArticleLookV2Mapper).insert(any(BizArticleLookV2.class));
    }

    // ==================== removeByPrimaryKey ====================

    @Test
    @DisplayName("removeByPrimaryKey - 删除成功，同时清理关联内容、标签、浏览和点赞记录")
    void removeByPrimaryKey_success() {
        when(bizArticleMapper.deleteByPrimaryKey(1L)).thenReturn(1);
        when(bizArticleTagsMapper.deleteByExample(any())).thenReturn(1);
        when(bizArticleLookV2Mapper.deleteByExample(any())).thenReturn(1);
        when(bizArticleLoveMapper.deleteByExample(any())).thenReturn(1);

        Example.Criteria mockCriteria = mock(Example.Criteria.class);
        try (MockedConstruction<Example> ignored = mockConstruction(Example.class, (mock, context) -> {
            when(mock.createCriteria()).thenReturn(mockCriteria);
        })) {
            boolean result = articleService.removeByPrimaryKey(1L);

            assertTrue(result);
            verify(articleContentService).removeByArticleId(1L);
            verify(bizArticleTagsMapper).deleteByExample(any());
            verify(bizArticleLookV2Mapper).deleteByExample(any());
            verify(bizArticleLoveMapper).deleteByExample(any());
        }
    }

    @Test
    @DisplayName("removeByPrimaryKey - 文章不存在时返回false")
    void removeByPrimaryKey_notExist_returnsFalse() {
        when(bizArticleMapper.deleteByPrimaryKey(999L)).thenReturn(0);
        when(bizArticleTagsMapper.deleteByExample(any())).thenReturn(0);
        when(bizArticleLookV2Mapper.deleteByExample(any())).thenReturn(0);
        when(bizArticleLoveMapper.deleteByExample(any())).thenReturn(0);

        Example.Criteria mockCriteria = mock(Example.Criteria.class);
        try (MockedConstruction<Example> ignored = mockConstruction(Example.class, (mock, context) -> {
            when(mock.createCriteria()).thenReturn(mockCriteria);
        })) {
            boolean result = articleService.removeByPrimaryKey(999L);
            assertFalse(result);
        }
    }

    // ==================== updateSelective ====================

    @Test
    @DisplayName("updateSelective - entity为null时抛出异常")
    void updateSelective_whenEntityIsNull_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> articleService.updateSelective(null));
    }

    @Test
    @DisplayName("updateSelective - 更新成功，同时更新内容")
    void updateSelective_success() {
        Article article = new Article();
        article.setId(1L);
        article.setTitle("Updated");
        article.setContent("<p>updated content</p>");
        article.setContentMd("# updated content");

        when(bizArticleMapper.updateByPrimaryKeySelective(any(BizArticle.class))).thenReturn(1);

        boolean result = articleService.updateSelective(article);

        assertTrue(result);
        verify(articleContentService).updateSelective(any(BizArticleContentBo.class));
    }

    @Test
    @DisplayName("updateSelective - mapper更新失败返回false时，不更新内容")
    void updateSelective_mapperFails_returnsFalse() {
        Article article = new Article();
        article.setId(1L);

        when(bizArticleMapper.updateByPrimaryKeySelective(any(BizArticle.class))).thenReturn(0);

        boolean result = articleService.updateSelective(article);

        assertFalse(result);
        verify(articleContentService, never()).updateSelective(any());
    }

    // ==================== getByPrimaryKey ====================

    @Test
    @DisplayName("getByPrimaryKey - primaryKey为null时抛出异常")
    void getByPrimaryKey_whenPrimaryKeyIsNull_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> articleService.getByPrimaryKey(null));
    }

    @Test
    @DisplayName("getByPrimaryKey - 文章不存在时返回null")
    void getByPrimaryKey_whenNotExist_returnsNull() {
        when(bizArticleMapper.get(999L)).thenReturn(null);

        Article result = articleService.getByPrimaryKey(999L);
        assertNull(result);
    }

    @Test
    @DisplayName("getByPrimaryKey - 正常返回文章，包含浏览数、评论数和点赞数")
    void getByPrimaryKey_success() {
        BizArticle entity = new BizArticle();
        entity.setId(1L);
        entity.setTitle("Test");

        when(bizArticleMapper.get(1L)).thenReturn(entity);

        BizArticleLookV2 lookV2 = new BizArticleLookV2();
        lookV2.setLookCount(100);

        Example.Criteria mockCriteria = mock(Example.Criteria.class);
        try (MockedConstruction<Example> ignored = mockConstruction(Example.class, (mock, context) -> {
            when(mock.createCriteria()).thenReturn(mockCriteria);
        })) {
            when(bizArticleLookV2Mapper.selectOneByExample(any())).thenReturn(lookV2);
            when(commentMapper.selectCountByExample(any())).thenReturn(5);
            when(bizArticleLoveMapper.selectCount(any())).thenReturn(10);

            Article result = articleService.getByPrimaryKey(1L);

            assertNotNull(result);
            assertEquals("Test", result.getTitle());
            assertEquals(100, result.getLookCount());
            assertEquals(5, result.getCommentCount());
            assertEquals(10, result.getLoveCount());
        }
    }

    @Test
    @DisplayName("getByPrimaryKey - 浏览记录为null时，浏览数为0")
    void getByPrimaryKey_noLookRecord_lookCountZero() {
        BizArticle entity = new BizArticle();
        entity.setId(1L);
        entity.setTitle("Test");

        when(bizArticleMapper.get(1L)).thenReturn(entity);

        Example.Criteria mockCriteria = mock(Example.Criteria.class);
        try (MockedConstruction<Example> ignored = mockConstruction(Example.class, (mock, context) -> {
            when(mock.createCriteria()).thenReturn(mockCriteria);
        })) {
            when(bizArticleLookV2Mapper.selectOneByExample(any())).thenReturn(null);
            when(commentMapper.selectCountByExample(any())).thenReturn(0);
            when(bizArticleLoveMapper.selectCount(any())).thenReturn(0);

            Article result = articleService.getByPrimaryKey(1L);

            assertNotNull(result);
            assertEquals(0, result.getLookCount());
        }
    }

    // ==================== listAll ====================

    @Test
    @DisplayName("listAll - 无数据时返回null")
    void listAll_empty_returnsNull() {
        when(bizArticleMapper.selectAll()).thenReturn(new ArrayList<>());
        assertNull(articleService.listAll());
    }

    @Test
    @DisplayName("listAll - 有数据时返回文章列表")
    void listAll_withData_returnsList() {
        BizArticle a1 = new BizArticle();
        a1.setId(1L);
        a1.setTitle("A1");
        BizArticle a2 = new BizArticle();
        a2.setId(2L);
        a2.setTitle("A2");

        when(bizArticleMapper.selectAll()).thenReturn(Arrays.asList(a1, a2));

        List<Article> result = articleService.listAll();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("A1", result.get(0).getTitle());
        assertEquals("A2", result.get(1).getTitle());
    }

    // ==================== listHotArticle ====================

    @Test
    @DisplayName("listHotArticle - 无数据时返回null")
    void listHotArticle_empty_returnsNull() {
        when(bizArticleMapper.listHotArticle()).thenReturn(new ArrayList<>());
        assertNull(articleService.listHotArticle(10));
    }

    @Test
    @DisplayName("listHotArticle - 有数据时返回热门文章列表")
    void listHotArticle_withData_returnsList() {
        BizArticle a1 = new BizArticle();
        a1.setId(1L);
        a1.setTitle("Hot Article");

        when(bizArticleMapper.listHotArticle()).thenReturn(Collections.singletonList(a1));

        List<Article> result = articleService.listHotArticle(10);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Hot Article", result.get(0).getTitle());
    }

    // ==================== listOfSitemap ====================

    @Test
    @DisplayName("listOfSitemap - 无数据时返回null")
    void listOfSitemap_empty_returnsNull() {
        when(bizArticleMapper.listOfSitemap()).thenReturn(new ArrayList<>());
        assertNull(articleService.listOfSitemap(10));
    }

    @Test
    @DisplayName("listOfSitemap - 有数据时返回站点地图文章列表")
    void listOfSitemap_withData_returnsList() {
        BizArticle a1 = new BizArticle();
        a1.setId(1L);

        when(bizArticleMapper.listOfSitemap()).thenReturn(Collections.singletonList(a1));

        List<Article> result = articleService.listOfSitemap(100);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    // ==================== getPrevAndNextArticles ====================

    @Test
    @DisplayName("getPrevAndNextArticles - 无数据时返回null")
    void getPrevAndNextArticles_empty_returnsNull() {
        when(bizArticleMapper.getPrevAndNextArticles(any(Date.class))).thenReturn(new ArrayList<>());
        assertNull(articleService.getPrevAndNextArticles(new Date()));
    }

    @Test
    @DisplayName("getPrevAndNextArticles - 正确返回上一篇和下一篇")
    void getPrevAndNextArticles_success() {
        Date now = new Date();
        Date prevTime = new Date(now.getTime() - 3600000);
        Date nextTime = new Date(now.getTime() + 3600000);

        BizArticle prev = new BizArticle();
        prev.setId(1L);
        prev.setTitle("Prev");
        prev.setCreateTime(prevTime);

        BizArticle next = new BizArticle();
        next.setId(2L);
        next.setTitle("Next");
        next.setCreateTime(nextTime);

        when(bizArticleMapper.getPrevAndNextArticles(any(Date.class))).thenReturn(Arrays.asList(prev, next));

        Map<String, Article> result = articleService.getPrevAndNextArticles(now);

        assertNotNull(result);
        assertNotNull(result.get("prev"));
        assertNotNull(result.get("next"));
        assertEquals("Prev", result.get("prev").getTitle());
        assertEquals("Next", result.get("next").getTitle());
    }

    @Test
    @DisplayName("getPrevAndNextArticles - insertTime为null时使用当前时间")
    void getPrevAndNextArticles_nullInsertTime_usesCurrentTime() {
        when(bizArticleMapper.getPrevAndNextArticles(any(Date.class))).thenReturn(new ArrayList<>());

        Map<String, Article> result = articleService.getPrevAndNextArticles(null);

        assertNull(result);
        verify(bizArticleMapper).getPrevAndNextArticles(any(Date.class));
    }

    // ==================== findPageBreakByCondition ====================

    @Test
    @DisplayName("findPageBreakByCondition - 无数据时返回null")
    void findPageBreakByCondition_empty_returnsNull() {
        ArticleConditionVO vo = new ArticleConditionVO();
        vo.setPageNumber(1);
        vo.setPageSize(10);

        when(bizArticleMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        PageInfo<Article> result = articleService.findPageBreakByCondition(vo);
        assertNull(result);
    }

    @Test
    @DisplayName("findPageBreakByCondition - 有数据时返回分页结果，包含标签和统计信息")
    void findPageBreakByCondition_withData_returnsPageInfo() {
        ArticleConditionVO vo = new ArticleConditionVO();
        vo.setPageNumber(1);
        vo.setPageSize(10);

        BizArticle a1 = new BizArticle();
        a1.setId(1L);
        a1.setTitle("Article 1");

        BizArticle tagArticle = new BizArticle();
        tagArticle.setId(1L);
        tagArticle.setTags(new ArrayList<>());

        when(bizArticleMapper.findPageBreakByCondition(any())).thenReturn(Collections.singletonList(a1));
        when(bizArticleMapper.listTagsByArticleId(any())).thenReturn(Collections.singletonList(tagArticle));
        when(bizStatisticsMapper.listArticleLookCountByArticleIds(any())).thenReturn(new ArrayList<>());
        when(bizStatisticsMapper.listArticleCommentCountByArticleIds(any())).thenReturn(new ArrayList<>());
        when(bizStatisticsMapper.listArticleLoveCountByArticleIds(any())).thenReturn(new ArrayList<>());

        PageInfo<Article> result = articleService.findPageBreakByCondition(vo);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
        assertEquals("Article 1", result.getList().get(0).getTitle());
    }

    @Test
    @DisplayName("findPageBreakByCondition - 统计信息为null时使用空列表")
    void findPageBreakByCondition_nullStatistics_usesEmptyList() {
        ArticleConditionVO vo = new ArticleConditionVO();
        vo.setPageNumber(1);
        vo.setPageSize(10);

        BizArticle a1 = new BizArticle();
        a1.setId(1L);
        a1.setTitle("Article 1");

        when(bizArticleMapper.findPageBreakByCondition(any())).thenReturn(Collections.singletonList(a1));
        when(bizArticleMapper.listTagsByArticleId(any())).thenReturn(new ArrayList<>());
        when(bizStatisticsMapper.listArticleLookCountByArticleIds(any())).thenReturn(null);
        when(bizStatisticsMapper.listArticleCommentCountByArticleIds(any())).thenReturn(null);
        when(bizStatisticsMapper.listArticleLoveCountByArticleIds(any())).thenReturn(null);

        PageInfo<Article> result = articleService.findPageBreakByCondition(vo);

        assertNotNull(result);
        assertEquals(1, result.getList().size());
    }

    // ==================== listRecommended ====================

    @Test
    @DisplayName("listRecommended - 无推荐文章时返回null")
    void listRecommended_empty_returnsNull() {
        when(bizArticleMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        List<Article> result = articleService.listRecommended(5);
        assertNull(result);
    }

    // ==================== listRecent ====================

    @Test
    @DisplayName("listRecent - 无近期文章时返回null")
    void listRecent_empty_returnsNull() {
        when(bizArticleMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        List<Article> result = articleService.listRecent(5);
        assertNull(result);
    }

    // ==================== listRandom ====================

    @Test
    @DisplayName("listRandom - 无随机文章时返回null")
    void listRandom_empty_returnsNull() {
        when(bizArticleMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        List<Article> result = articleService.listRandom(5);
        assertNull(result);
    }

    // ==================== listRelatedArticle ====================

    @Test
    @DisplayName("listRelatedArticle - article为null时降级为随机文章")
    void listRelatedArticle_articleNull_fallsBackToRandom() {
        when(bizArticleMapper.findPageBreakByCondition(any())).thenReturn(new ArrayList<>());

        List<Article> result = articleService.listRelatedArticle(5, null);
        assertNull(result);
    }

    // ==================== publish ====================

    @Test
    @DisplayName("publish - tags为null时抛出异常")
    void publish_whenTagsNull_throwsException() {
        Article article = new Article();
        assertThrows(ZhydArticleException.class, () -> articleService.publish(article, null, null));
    }

    @Test
    @DisplayName("publish - tags为空数组时抛出异常")
    void publish_whenTagsEmpty_throwsException() {
        Article article = new Article();
        assertThrows(ZhydArticleException.class, () -> articleService.publish(article, new Long[0], null));
    }

    // ==================== updateTopOrRecommendedById ====================

    @Test
    @DisplayName("updateTopOrRecommendedById - type为top时切换置顶状态")
    void updateTopOrRecommendedById_top_togglesTop() {
        BizArticle article = new BizArticle();
        article.setId(1L);
        article.setTop(false);

        when(bizArticleMapper.selectByPrimaryKey(1L)).thenReturn(article);
        when(bizArticleMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        boolean result = articleService.updateTopOrRecommendedById("top", 1L);

        assertTrue(result);
        assertTrue(article.getTop());
    }

    @Test
    @DisplayName("updateTopOrRecommendedById - type为recommend时切换推荐状态")
    void updateTopOrRecommendedById_recommend_togglesRecommended() {
        BizArticle article = new BizArticle();
        article.setId(1L);
        article.setRecommended(false);

        when(bizArticleMapper.selectByPrimaryKey(1L)).thenReturn(article);
        when(bizArticleMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        boolean result = articleService.updateTopOrRecommendedById("recommend", 1L);

        assertTrue(result);
        assertTrue(article.getRecommended());
    }

    @Test
    @DisplayName("updateTopOrRecommendedById - type为comment时切换评论状态")
    void updateTopOrRecommendedById_comment_togglesComment() {
        BizArticle article = new BizArticle();
        article.setId(1L);
        article.setComment(false);

        when(bizArticleMapper.selectByPrimaryKey(1L)).thenReturn(article);
        when(bizArticleMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        boolean result = articleService.updateTopOrRecommendedById("comment", 1L);

        assertTrue(result);
        assertTrue(article.getComment());
    }

    @Test
    @DisplayName("updateTopOrRecommendedById - type无效时抛出异常")
    void updateTopOrRecommendedById_invalidType_throwsException() {
        BizArticle article = new BizArticle();
        article.setId(1L);

        when(bizArticleMapper.selectByPrimaryKey(1L)).thenReturn(article);

        assertThrows(ZhydException.class, () -> articleService.updateTopOrRecommendedById("invalid", 1L));
    }

    // ==================== batchUpdateStatus ====================

    @Test
    @DisplayName("batchUpdateStatus - ids为null时不执行操作")
    void batchUpdateStatus_nullIds_noOp() {
        articleService.batchUpdateStatus(null, true);
        verify(bizArticleMapper, never()).batchUpdateStatus(any(), anyBoolean());
    }

    @Test
    @DisplayName("batchUpdateStatus - ids为空数组时不执行操作")
    void batchUpdateStatus_emptyIds_noOp() {
        articleService.batchUpdateStatus(new Long[0], true);
        verify(bizArticleMapper, never()).batchUpdateStatus(any(), anyBoolean());
    }

    @Test
    @DisplayName("batchUpdateStatus - 正常批量更新状态")
    void batchUpdateStatus_success() {
        Long[] ids = {1L, 2L, 3L};
        articleService.batchUpdateStatus(ids, true);
        verify(bizArticleMapper).batchUpdateStatus(any(), eq(true));
    }
}
