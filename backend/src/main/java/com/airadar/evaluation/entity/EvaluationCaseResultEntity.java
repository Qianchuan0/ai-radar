package com.airadar.evaluation.entity;

import com.airadar.common.persistence.JsonbTypeHandler;
import com.airadar.evaluation.model.EvaluationCaseStatus;
import com.airadar.evaluation.model.EvaluationCaseType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

@TableName(value = "evaluation_case_result", autoResultMap = true)
public class EvaluationCaseResultEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long runId;
    private Long caseId;
    private String caseCode;
    private EvaluationCaseType caseType;
    private EvaluationCaseStatus status;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private JsonNode actualPayload;
    private String failureReason;
    private Instant evaluatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public Long getCaseId() {
        return caseId;
    }

    public void setCaseId(Long caseId) {
        this.caseId = caseId;
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

    public EvaluationCaseStatus getStatus() {
        return status;
    }

    public void setStatus(EvaluationCaseStatus status) {
        this.status = status;
    }

    public JsonNode getActualPayload() {
        return actualPayload;
    }

    public void setActualPayload(JsonNode actualPayload) {
        this.actualPayload = actualPayload;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(Instant evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }
}
