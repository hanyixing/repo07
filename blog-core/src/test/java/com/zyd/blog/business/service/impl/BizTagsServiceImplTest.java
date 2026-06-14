package com.zyd.blog.business.service.impl;

import com.zyd.blog.business.entity.Tags;
import com.zyd.blog.framework.exception.ZhydException;
import com.zyd.blog.persistence.beans.BizArticleTags;
import com.zyd.blog.persistence.beans.BizTags;
import com.zyd.blog.persistence.mapper.BizArticleTagsMapper;
import com.zyd.blog.persistence.mapper.BizTagsMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * BizTagsServiceImpl 单元测试。
 * 覆盖标签的查询、唯一性校验以及删除前的关联校验等业务分支。
 */
@ExtendWith(MockitoExtension.class)
class BizTagsServiceImplTest {

    @Mock
    private BizTagsMapper bizTagsMapper;
    @Mock
    private BizArticleTagsMapper bizArticleTagsMapper;

    @InjectMocks
    private BizTagsServiceImpl bizTagsService;

    @Test
    void getByName_returnsNull_whenNameBlank() {
        assertNull(bizTagsService.getByName(""));
        assertNull(bizTagsService.getByName(null));
        verifyNoInteractions(bizTagsMapper);
    }

    @Test
    void getByName_returnsTags_whenFound() {
        BizTags found = new BizTags();
        found.setId(1L);
        found.setName("java");
        when(bizTagsMapper.selectOne(any())).thenReturn(found);

        Tags tags = bizTagsService.getByName("java");

        assertNotNull(tags);
        assertEquals("java", tags.getName());
    }

    @Test
    void insert_throws_whenEntityNull() {
        assertThrows(IllegalArgumentException.class, () -> bizTagsService.insert(null));
    }

    @Test
    void insert_throws_whenTagAlreadyExists() {
        BizTags existing = new BizTags();
        existing.setId(1L);
        existing.setName("java");
        when(bizTagsMapper.selectOne(any())).thenReturn(existing);

        Tags entity = new Tags();
        entity.setName("java");

        assertThrows(ZhydException.class, () -> bizTagsService.insert(entity));
    }

    @Test
    void insert_savesTag_whenNameUnique() {
        when(bizTagsMapper.selectOne(any())).thenReturn(null);
        Tags entity = new Tags();
        entity.setName("java");

        Tags result = bizTagsService.insert(entity);

        assertSame(entity, result);
        verify(bizTagsMapper).insertSelective(entity.getBizTags());
    }

    @Test
    void removeByPrimaryKey_throws_whenTagStillUsedByArticles() {
        List<BizArticleTags> relations = new ArrayList<>();
        relations.add(new BizArticleTags());
        when(bizArticleTagsMapper.select(any())).thenReturn(relations);

        assertThrows(ZhydException.class, () -> bizTagsService.removeByPrimaryKey(1L));
    }

    @Test
    void removeByPrimaryKey_returnsTrue_whenTagUnused() {
        when(bizArticleTagsMapper.select(any())).thenReturn(Collections.emptyList());
        when(bizTagsMapper.deleteByPrimaryKey(1L)).thenReturn(1);

        assertTrue(bizTagsService.removeByPrimaryKey(1L));
    }

    @Test
    void updateSelective_throws_whenEntityNull() {
        assertThrows(IllegalArgumentException.class, () -> bizTagsService.updateSelective(null));
    }

    @Test
    void updateSelective_throws_whenNameUsedByAnotherTag() {
        BizTags existing = new BizTags();
        existing.setId(2L);
        existing.setName("java");
        when(bizTagsMapper.selectOne(any())).thenReturn(existing);

        Tags entity = new Tags();
        entity.setId(1L);
        entity.setName("java");

        assertThrows(ZhydException.class, () -> bizTagsService.updateSelective(entity));
    }

    @Test
    void updateSelective_returnsTrue_whenNameAvailable() {
        when(bizTagsMapper.selectOne(any())).thenReturn(null);
        when(bizTagsMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        Tags entity = new Tags();
        entity.setId(1L);
        entity.setName("java");

        assertTrue(bizTagsService.updateSelective(entity));
        verify(bizTagsMapper).updateByPrimaryKeySelective(entity.getBizTags());
    }

    @Test
    void getByPrimaryKey_throws_whenKeyNull() {
        assertThrows(IllegalArgumentException.class, () -> bizTagsService.getByPrimaryKey(null));
    }

    @Test
    void getByPrimaryKey_returnsNull_whenNotFound() {
        when(bizTagsMapper.selectByPrimaryKey(9L)).thenReturn(null);

        assertNull(bizTagsService.getByPrimaryKey(9L));
    }

    @Test
    void getByPrimaryKey_returnsTags_whenFound() {
        BizTags biz = new BizTags();
        biz.setId(5L);
        biz.setName("spring");
        when(bizTagsMapper.selectByPrimaryKey(5L)).thenReturn(biz);

        Tags tags = bizTagsService.getByPrimaryKey(5L);

        assertNotNull(tags);
        assertEquals(5L, tags.getId());
    }

    @Test
    void listAll_returnsNull_whenEmpty() {
        when(bizTagsMapper.selectAll()).thenReturn(new ArrayList<>());

        assertNull(bizTagsService.listAll());
    }

    @Test
    void listAll_returnsTags_whenPresent() {
        List<BizTags> data = new ArrayList<>();
        data.add(new BizTags());
        when(bizTagsMapper.selectAll()).thenReturn(data);

        List<Tags> result = bizTagsService.listAll();

        assertNotNull(result);
        assertEquals(1, result.size());
    }
}
