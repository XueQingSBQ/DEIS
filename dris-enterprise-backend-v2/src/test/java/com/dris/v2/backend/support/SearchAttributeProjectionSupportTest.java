package com.dris.v2.backend.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchAttributeProjectionSupportTest {

    @Test
    void shouldExtractStructuredFieldsFromAdditionalAttributes() {
        String json = "{\"column\":\"规范文件\",\"region\":\"湖南省\",\"nodeTarget\":\"水利部\",\"sourceFilename\":\"山洪灾害分析评价技术要求.pdf\",\"keywords\":[\"山洪灾害\",\"分析评价\",\"技术要求\"]}";

        assertEquals("规范文件", SearchAttributeProjectionSupport.extractTheme(json));
        assertEquals("湖南省", SearchAttributeProjectionSupport.extractRegion(json));
        assertEquals("水利部", SearchAttributeProjectionSupport.extractNodeTarget(json));
        assertEquals("山洪灾害分析评价技术要求.pdf", SearchAttributeProjectionSupport.extractSourceFilename(json));

        List<String> keywords = SearchAttributeProjectionSupport.extractKeywords(json);
        assertEquals(3, keywords.size());
        assertEquals("山洪灾害 分析评价 技术要求", SearchAttributeProjectionSupport.extractKeywordsText(json));
    }
}
