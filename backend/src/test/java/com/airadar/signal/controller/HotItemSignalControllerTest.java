package com.airadar.signal.controller;

import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
import com.airadar.signal.model.GrowthConfidence;
import com.airadar.signal.model.GrowthMetrics;
import com.airadar.signal.service.GrowthCalculationService;
import com.airadar.signal.service.SignalSnapshotService;
import com.airadar.signal.vo.SignalSnapshotVO;
import com.airadar.signal.model.SourceRole;
import com.airadar.source.model.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HotItemSignalController.class)
@Import(com.airadar.common.exception.GlobalExceptionHandler.class)
class HotItemSignalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SignalSnapshotService signalSnapshotService;

    @MockitoBean
    private GrowthCalculationService growthCalculationService;

    @Test
    void shouldReturnRecentSnapshots() throws Exception {
        SignalSnapshotVO snapshot = new SignalSnapshotVO(
            1L,
            101L,
            201L,
            SourceType.GITHUB,
            SourceRole.ADOPTION,
            Instant.parse("2026-07-17T00:00:00Z"),
            objectMapper.createObjectNode().put("stargazersCount", 120),
            objectMapper.createObjectNode().put("adoption", 55.0),
            Instant.parse("2026-07-17T00:00:01Z")
        );
        when(signalSnapshotService.listRecent(101L, 5)).thenReturn(List.of(snapshot));

        mockMvc.perform(get("/api/v1/hot-items/101/signals")
                .param("limit", "5")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data[0].hotItemId").value(101))
            .andExpect(jsonPath("$.data[0].sourceType").value("GITHUB"))
            .andExpect(jsonPath("$.data[0].sourceRole").value("ADOPTION"));
    }

    @Test
    void shouldReturnGrowthTrend() throws Exception {
        when(growthCalculationService.calculate(101L, "24h")).thenReturn(new GrowthMetrics(
            101L,
            "24h",
            12.5,
            3.0,
            20.0,
            null,
            null,
            8.875,
            GrowthConfidence.HIGH
        ));

        mockMvc.perform(get("/api/v1/hot-items/101/trend")
                .param("window", "24h")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.hotItemId").value(101))
            .andExpect(jsonPath("$.data.window").value("24h"))
            .andExpect(jsonPath("$.data.confidence").value("HIGH"));
    }

    @Test
    void shouldTranslateBusinessExceptions() throws Exception {
        when(growthCalculationService.calculate(999L, "24h"))
            .thenThrow(new BusinessException(ErrorCode.HOT_ITEM_NOT_FOUND));

        mockMvc.perform(get("/api/v1/hot-items/999/trend").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ITEM.NOT_FOUND"));
    }
}
