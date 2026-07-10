package com.airadar.evaluation.entity;

import com.airadar.common.persistence.JsonbTypeHandler;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

@TableName(value = "evaluation_case", autoResultMap = true)
public class EvaluationCaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long datasetId;
    private String caseCode;
    private EvaluationCaseType caseType;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode targetPayload;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode expectedPayload;
    private String notes;
    private Boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(Long datasetId) {
        this.datasetId = datasetId;
    }

    public String getCaseCode() {
        return caseCode;
    }

    public void setCaseCode(String caseCode) {
        this.caseCode = caseCode;
    }

    public EvaluationCaseType getCaseType() {
        return caseType;
    }

    public void setCaseType(EvaluationCaseType caseType) {
        this.caseType = caseType;
    }

    public JsonNode getTargetPayload() {
        return targetPayload;
    }

    public void setTargetPayload(JsonNode targetPayload) {
        this.targetPayload = targetPayload;
    }

    public JsonNode getExpectedPayload() {
        return expectedPayload;
    }

    public void setExpectedPayload(JsonNode expectedPayload) {
        this.expectedPayload = expectedPayload;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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
