package com.airadar.report.entity;

import com.airadar.common.persistence.JsonbTypeHandler;
import com.airadar.report.model.ReportStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.time.LocalDate;

@TableName(value = "daily_report", autoResultMap = true)
public class DailyReportEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private LocalDate reportDate;
    private ReportStatus status;
    private String title;
    private String summary;
    private Integer clusterCount;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode topClusterIds;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode contentPayload;
    private Instant generatedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getReportDate() {
        return reportDate;
    }

    public void setReportDate(LocalDate reportDate) {
        this.reportDate = reportDate;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getClusterCount() {
        return clusterCount;
    }

    public void setClusterCount(Integer clusterCount) {
        this.clusterCount = clusterCount;
    }

    public JsonNode getTopClusterIds() {
        return topClusterIds;
    }

    public void setTopClusterIds(JsonNode topClusterIds) {
        this.topClusterIds = topClusterIds;
    }

    public JsonNode getContentPayload() {
        return contentPayload;
    }

    public void setContentPayload(JsonNode contentPayload) {
        this.contentPayload = contentPayload;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
