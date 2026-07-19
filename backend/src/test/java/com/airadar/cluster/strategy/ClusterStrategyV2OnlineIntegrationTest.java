package com.airadar.cluster.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.feature.HotItemFeatureEntity;
import com.airadar.cluster.feature.HotItemFeatureMapper;
import com.airadar.cluster.feature.extractor.ItemFeatureExtractor;
import com.airadar.cluster.governance.ClusterMembershipHistoryEntity;
import com.airadar.cluster.governance.ClusterMembershipHistoryMapper;
import com.airadar.cluster.governance.ClusterReviewService;
import com.airadar.cluster.governance.ClusterReviewTaskEntity;
import com.airadar.cluster.governance.ClusterReviewTaskMapper;
import com.airadar.cluster.governance.ReviewTaskStatus;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.strategy.controller.ClusterStrategyController;
import com.airadar.cluster.strategy.vo.ClusterStrategyStatusVO;
import com.airadar.crawl.entity.CrawlTaskEntity;
import com.airadar.crawl.mapper.CrawlTaskMapper;
import com.airadar.crawl.model.CrawlTaskStatus;
import com.airadar.crawl.model.CrawlTriggerType;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.mapper.HotItemMapper;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.raw.mapper.RawItemMapper;
import com.airadar.source.entity.SourceConfigEntity;
import com.airadar.source.mapper.SourceConfigMapper;
import com.airadar.source.model.SourceType;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 17C integration coverage: V2 online gradual adoption.
 *
 * <p>Each test method runs in its own transaction so destructive cleanup
 * is unnecessary. The shared {@link ClusterStrategyProperties} bean is
 * mutated per-test through {@link #enableV2Online(boolean, int, java.util.List)}
 * and reset in {@link AfterEach} so tests cannot leak configuration into
 * each other.
 */
@Testcontainers
@SpringBootTest(properties = {
        "ai-radar.operations.scheduled-crawl.enabled=false",
        "ai-radar.operations.scheduled-daily-report.enabled=false"
})
@Transactional
class ClusterStrategyV2OnlineIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ClusterStrategyProperties properties;
    @Autowired
    private ClusterAssignmentOrchestrator orchestrator;
    @Autowired
    private V2OnlineAssignmentService v2OnlineService;
    @Autowired
    private ClusterStrategyController strategyController;
    @Autowired
    private ClusterReviewService reviewService;
    @Autowired
    private EventRuleClusterStrategy v2Strategy;
    @Autowired
    private ItemFeatureExtractor featureExtractor;
    @Autowired
    private HotClusterMapper clusterMapper;
    @Autowired
    private HotClusterItemMapper clusterItemMapper;
    @Autowired
    private HotItemMapper hotItemMapper;
    @Autowired
    private HotItemFeatureMapper featureMapper;
    @Autowired
    private ClusterMatchDecisionMapper decisionMapper;
    @Autowired
    private ClusterReviewTaskMapper reviewTaskMapper;
    @Autowired
    private ClusterMembershipHistoryMapper historyMapper;
    @Autowired
    private SourceConfigMapper sourceConfigMapper;
    @Autowired
    private CrawlTaskMapper crawlTaskMapper;
    @Autowired
    private RawItemMapper rawItemMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void resetProperties() {
        properties.setStrategy("hn-rule-v1");
        properties.setShadowStrategy(null);
        ClusterStrategyProperties.V2Online reset = new ClusterStrategyProperties.V2Online();
        properties.setV2Online(reset);
    }

    @Test
    void defaultConfigReturnsV1OnlyStatusAndWritesNoDecisions() {
        ClusterStrategyStatusVO status = strategyController.status().data();
        assertThat(status.onlineStrategy()).isEqualTo("hn-rule-v1");
        assertThat(status.shadowEnabled()).isFalse();
        assertThat(status.v2OnlineEnabled()).isFalse();
        assertThat(status.rolloutStage()).isEqualTo("V1_ONLY");

        Fixture fixture = Fixture.create(this);
        HotItemEntity item = fixture.createItem("solo", "https://example.com/solo");
        ClusterAssignmentResult result = orchestrator.assign(item);

        assertThat(result.getCluster()).isNotNull();
        assertThat(result.getRuleVersion()).isEqualTo("hn-rule-v1");
        List<ClusterMatchDecisionEntity> decisions = decisionsFor(item.getId());
        assertThat(decisions).isEmpty();
    }

    @Test
    void shadowStrategyWritesDecisionsWithoutMembershipChange() {
        properties.setShadowStrategy("event-rule-v2");

        Fixture fixture = Fixture.create(this);
        HotItemEntity item = fixture.createItem("shadow", "https://example.com/shadow");
        ClusterAssignmentResult result = orchestrator.assign(item);

        assertThat(result.getRuleVersion()).isEqualTo("hn-rule-v1");
        List<ClusterMatchDecisionEntity> decisions = decisionsFor(item.getId());
        assertThat(decisions).isNotEmpty();
        // Shadow never creates a V2 membership — every decision row is
        // bookkeeping only.
        assertThat(activeClusterOf(item.getId())).isNotNull();
    }

    @Test
    void v2OnlineDisabledDoesNotInvokeV2Writer() {
        // enabled=false but traffic 100% — orchestrator must skip V2 entirely.
        enableV2Online(false, 100, List.of("L1"));

        Fixture fixture = Fixture.create(this);
        HotItemEntity item = fixture.createItem("disabled", "https://example.com/disabled");
        orchestrator.assign(item);

        List<ClusterMatchDecisionEntity> decisions = decisionsFor(item.getId());
        assertThat(decisions).isEmpty();
    }

    @Test
    void v2OnlineMovesItemOnArxivL1Match() {
        enableV2Online(true, 100, List.of("L1"));

        Fixture fixture = Fixture.create(this);

        // Candidate: a discussion page that mentions arXiv 2401.00500.
        // V1 only canonicalizes the URL — it cannot tell this item relates
        // to the arXiv paper, so V1 leaves it in its own singleton.
        HotClusterEntity candidateCluster = fixture.createCluster("candidate");
        HotItemEntity candidate = fixture.createItemWithArxivReference(
                "candidate", "https://example.com/discuss-paper");
        fixture.addMembership(candidateCluster, candidate, true);
        featureExtractor.extractAndPersist(candidate);

        // New item: the actual arXiv URL. V1 sees a brand-new canonical URL
        // and creates a singleton. V2 extracts arxiv=2401.00500, finds the
        // candidate via external_id overlap, L1 ACCEPTS, and the orchestrator
        // moves the item.
        HotItemEntity newItem = fixture.createItemWithArxivReference(
                "paper", "https://arxiv.org/abs/2401.00500");
        ClusterAssignmentResult result = orchestrator.assign(newItem);

        // After V2 online: item is now in the candidate cluster, singleton
        // closed as MERGED, history row written by MoveItemService.
        HotClusterItemEntity membership = activeMembershipOf(newItem.getId());
        assertThat(membership).isNotNull();
        assertThat(membership.getHotClusterId()).isEqualTo(candidateCluster.getId());
        HotClusterEntity v1Singleton = clusterMapper.selectById(result.getCluster().getId());
        // If V1 actually created a singleton and V2 moved out, the source
        // cluster is closed. If V1 already merged (rare), the membership
        // ends up in candidateCluster anyway.
        if (v1Singleton != null && !candidateCluster.getId().equals(v1Singleton.getId())) {
            assertThat(v1Singleton.getStatus()).isEqualTo("MERGED");
            assertThat(v1Singleton.getMergedIntoClusterId()).isEqualTo(candidateCluster.getId());
        }

        List<ClusterMatchDecisionEntity> decisions = decisionsFor(newItem.getId());
        assertThat(decisions).isNotEmpty();
        assertThat(decisions).anyMatch(d -> "ACCEPTED".equals(d.getDecision()));

        List<ClusterMembershipHistoryEntity> histories = historyMapper.selectList(
                new LambdaQueryWrapper<ClusterMembershipHistoryEntity>()
                        .eq(ClusterMembershipHistoryEntity::getHotItemId, newItem.getId()));
        assertThat(histories).isNotEmpty();
        // The move is attributed to the SYSTEM operator on behalf of V2 online.
        assertThat(histories).anyMatch(h -> "MOVE".equals(h.getAction()));
    }

    @Test
    void v2OnlineLevelFilterBlocksUnapprovedLayer() {
        // Allow only L2 — even if V2 L1 ACCEPTS, the orchestrator must refuse
        // to move the item.
        enableV2Online(true, 100, List.of("L2"));

        Fixture fixture = Fixture.create(this);
        HotClusterEntity candidateCluster = fixture.createCluster("candidate");
        HotItemEntity candidate = fixture.createItemWithArxivReference(
                "candidate", "https://example.com/discuss-paper-l2");
        fixture.addMembership(candidateCluster, candidate, true);
        featureExtractor.extractAndPersist(candidate);

        HotItemEntity newItem = fixture.createItemWithArxivReference(
                "paper", "https://arxiv.org/abs/2401.00500");
        orchestrator.assign(newItem);

        // V2 L1 ACCEPTED was produced but the level filter blocked the move,
        // so the item stays in the V1 path's singleton.
        HotClusterItemEntity membership = activeMembershipOf(newItem.getId());
        assertThat(membership).isNotNull();
        assertThat(membership.getHotClusterId()).isNotEqualTo(candidateCluster.getId());

        List<ClusterMatchDecisionEntity> decisions = decisionsFor(newItem.getId());
        assertThat(decisions).anyMatch(d -> "ACCEPTED".equals(d.getDecision()));
    }

    @Test
    void v2OnlineMaterializesReviewTaskForReviewRequiredDecisions() {
        enableV2Online(true, 100, List.of("L1"));

        Fixture fixture = Fixture.create(this);
        HotClusterEntity candidateCluster = fixture.createCluster("candidate");
        HotItemEntity candidate = fixture.createItem(
                "candidate", "https://example.com/review-candidate");
        fixture.addMembership(candidateCluster, candidate, true);
        featureExtractor.extractAndPersist(candidate);

        // Manually insert a REVIEW_REQUIRED decision referencing the candidate.
        // This simulates V2 producing a grey-zone match that the online
        // writer cannot auto-resolve.
        HotItemEntity newItem = fixture.createItem(
                "review", "https://example.com/review-new");
        ClusterMatchDecisionEntity reviewDecision = new ClusterMatchDecisionEntity();
        reviewDecision.setHotItemId(newItem.getId());
        reviewDecision.setCandidateClusterId(candidateCluster.getId());
        reviewDecision.setDecision("REVIEW_REQUIRED");
        reviewDecision.setMatchScore(new BigDecimal("0.70"));
        reviewDecision.setMatchMethod("SIMILARITY");
        reviewDecision.setMatchReason(objectMapper.createObjectNode());
        reviewDecision.setRuleVersion("event-rule-v2");
        reviewDecision.setDecidedAt(Instant.now());
        decisionMapper.insert(reviewDecision);

        // Trigger materialization through the V2 online service path; the
        // public method is the same one the orchestrator calls.
        reviewService.materializeOpenTasks();

        List<ClusterReviewTaskEntity> tasks = reviewTaskMapper.selectList(
                new LambdaQueryWrapper<ClusterReviewTaskEntity>()
                        .eq(ClusterReviewTaskEntity::getClusterMatchDecisionId, reviewDecision.getId()));
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getStatus()).isEqualTo(ReviewTaskStatus.OPEN.name());

        // The review item has no V2 online move (REVIEW_REQUIRED does not
        // auto-merge) but its decision still surfaces in the queue.
        assertThat(activeMembershipOf(newItem.getId())).isNull();
    }

    @Test
    void statusEndpointReflectsRolloutStage() {
        // Default → V1_ONLY
        assertThat(strategyController.status().data().rolloutStage()).isEqualTo("V1_ONLY");

        properties.setShadowStrategy("event-rule-v2");
        ClusterStrategyStatusVO shadow = strategyController.status().data();
        assertThat(shadow.rolloutStage()).isEqualTo("SHADOW_ONLY");
        assertThat(shadow.shadowEnabled()).isTrue();

        enableV2Online(true, 25, List.of("L1"));
        ClusterStrategyStatusVO stage2 = strategyController.status().data();
        assertThat(stage2.v2OnlineEnabled()).isTrue();
        assertThat(stage2.rolloutStage()).isEqualTo("STAGE_2_L1");
        assertThat(stage2.trafficPercent()).isEqualTo(25);

        enableV2Online(true, 50, List.of("L1", "L2"));
        assertThat(strategyController.status().data().rolloutStage()).isEqualTo("STAGE_3_L2");

        enableV2Online(true, 100, List.of("L1", "L2", "L3"));
        assertThat(strategyController.status().data().rolloutStage()).isEqualTo("STAGE_4_L3");
    }

    @Test
    void v2OnlinePropertiesValidationRejectsBadConfigs() {
        // traffic-percent=0 + enabled=true is rejected — a 0% rollout is
        // what shadow-strategy is for.
        properties.getV2Online().setEnabled(true);
        properties.getV2Online().setTrafficPercent(0);
        properties.getV2Online().setAllowedMatchLevels(List.of("L1"));
        assertThatThrownByValidation();
        properties.getV2Online().setEnabled(false);

        // enabled=true with no allowed levels is rejected.
        properties.getV2Online().setEnabled(true);
        properties.getV2Online().setTrafficPercent(100);
        properties.getV2Online().setAllowedMatchLevels(List.of());
        assertThatThrownByValidation();
        properties.getV2Online().setAllowedMatchLevels(List.of("L1"));

        // unknown level value is rejected.
        properties.getV2Online().setAllowedMatchLevels(List.of("L9"));
        assertThatThrownByValidation();
        properties.getV2Online().setAllowedMatchLevels(List.of("L1"));

        // out-of-range l3-min-score is rejected.
        properties.getV2Online().setL3MinScore(1.5);
        assertThatThrownByValidation();
        properties.getV2Online().setL3MinScore(0.85);

        // out-of-range traffic-percent is rejected.
        properties.getV2Online().setTrafficPercent(150);
        assertThatThrownByValidation();
        properties.getV2Online().setTrafficPercent(100);

        // strategy=event-rule-v2 is still rejected — V2 never becomes the
        // sole online strategy through the back door.
        properties.setStrategy("event-rule-v2");
        assertThatThrownByValidation();
        properties.setStrategy("hn-rule-v1");

        // Sanity check: a clean config validates.
        properties.getV2Online().setEnabled(true);
        properties.getV2Online().setTrafficPercent(100);
        properties.getV2Online().setAllowedMatchLevels(List.of("L1", "L2"));
        properties.validate();
    }

    private void assertThatThrownByValidation() {
        try {
            properties.validate();
            throw new AssertionError("Expected IllegalStateException from validate()");
        } catch (IllegalStateException expected) {
            // expected
        }
    }

    private void enableV2Online(boolean enabled, int trafficPercent, List<String> levels) {
        ClusterStrategyProperties.V2Online v2 = new ClusterStrategyProperties.V2Online();
        v2.setEnabled(enabled);
        v2.setTrafficPercent(trafficPercent);
        v2.setAllowedMatchLevels(levels);
        v2.setL3MinScore(0.85);
        v2.setReviewRequiredToQueue(true);
        v2.setSourceAllowlist(List.of());
        properties.setV2Online(v2);
    }

    private List<ClusterMatchDecisionEntity> decisionsFor(long hotItemId) {
        return decisionMapper.selectList(
                new LambdaQueryWrapper<ClusterMatchDecisionEntity>()
                        .eq(ClusterMatchDecisionEntity::getHotItemId, hotItemId));
    }

    private HotClusterItemEntity activeMembershipOf(long hotItemId) {
        return clusterItemMapper.selectOne(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotItemId, hotItemId)
                        .isNull(HotClusterItemEntity::getRemovedAt));
    }

    private Long activeClusterOf(long hotItemId) {
        HotClusterItemEntity membership = activeMembershipOf(hotItemId);
        return membership == null ? null : membership.getHotClusterId();
    }

    /**
     * Minimal fixture that seeds the source_config -> crawl_task -> raw_item
     * -> hot_item chain plus a few helpers for V2-specific setups.
     */
    static final class Fixture {

        private static final String ARXIV_ID = "2401.00500";
        private final ClusterStrategyV2OnlineIntegrationTest owner;
        private final String suffix = UUID.randomUUID().toString().substring(0, 8);
        private SourceConfigEntity sourceConfig;
        private CrawlTaskEntity crawlTask;

        private Fixture(ClusterStrategyV2OnlineIntegrationTest owner) {
            this.owner = owner;
        }

        static Fixture create(ClusterStrategyV2OnlineIntegrationTest owner) {
            Fixture f = new Fixture(owner);
            f.seedSource();
            return f;
        }

        private void seedSource() {
            Instant now = Instant.now();
            SourceConfigEntity source = new SourceConfigEntity();
            source.setSourceCode("v2online-" + suffix);
            source.setSourceType(SourceType.HACKER_NEWS);
            source.setDisplayName("V2 online test " + suffix);
            source.setEnabled(Boolean.TRUE);
            source.setConfigPayload(owner.objectMapper.createObjectNode());
            source.setVersion(0);
            source.setCreatedAt(now);
            source.setUpdatedAt(now);
            owner.sourceConfigMapper.insert(source);
            this.sourceConfig = source;

            CrawlTaskEntity task = new CrawlTaskEntity();
            task.setSourceConfigId(source.getId());
            task.setTriggerType(CrawlTriggerType.MANUAL);
            task.setStatus(CrawlTaskStatus.SUCCEEDED);
            task.setRequestedAt(now);
            task.setStartedAt(now);
            task.setFinishedAt(now);
            task.setFetchedCount(0);
            task.setPersistedCount(0);
            task.setMatchedCount(0);
            task.setFailedCount(0);
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            owner.crawlTaskMapper.insert(task);
            this.crawlTask = task;
        }

        HotClusterEntity createCluster(String label) {
            Instant now = Instant.now();
            HotClusterEntity cluster = new HotClusterEntity();
            cluster.setTitle(label + "-" + suffix);
            cluster.setSummary(label);
            cluster.setStatus("ACTIVE");
            cluster.setFirstSeenAt(now);
            cluster.setLastSeenAt(now);
            cluster.setVersion(0);
            cluster.setCreatedAt(now);
            cluster.setUpdatedAt(now);
            owner.clusterMapper.insert(cluster);
            return cluster;
        }

        HotItemEntity createItem(String label, String url) {
            Instant now = Instant.now();
            RawItemEntity raw = new RawItemEntity();
            raw.setCrawlTaskId(crawlTask.getId());
            raw.setSourceType(SourceType.HACKER_NEWS);
            raw.setExternalId(label + "-" + suffix);
            raw.setSourceUrl(url);
            raw.setRawPayload(owner.objectMapper.createObjectNode());
            raw.setPayloadHash(label + "-" + suffix);
            raw.setFetchedAt(now);
            raw.setCreatedAt(now);
            owner.rawItemMapper.insert(raw);

            HotItemEntity item = new HotItemEntity();
            item.setLatestRawItemId(raw.getId());
            item.setSourceType(SourceType.HACKER_NEWS);
            item.setExternalId(label + "-" + suffix);
            item.setItemType("STORY");
            item.setTitle(label);
            item.setSourceUrl(url);
            item.setContentHash(label + "-" + suffix);
            item.setFirstSeenAt(now);
            item.setLastSeenAt(now);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            item.setTags(owner.objectMapper.createArrayNode());
            item.setMetrics(owner.objectMapper.createObjectNode());
            owner.hotItemMapper.insert(item);
            return item;
        }

        /**
         * Creates a hot item whose title mentions {@code arXiv 2401.00500}
         * so the external-id extractor can pull the arxiv id out of the
         * title even when the URL itself is not an arxiv URL.
         */
        HotItemEntity createItemWithArxivReference(String label, String url) {
            Instant now = Instant.now();
            RawItemEntity raw = new RawItemEntity();
            raw.setCrawlTaskId(crawlTask.getId());
            raw.setSourceType(SourceType.HACKER_NEWS);
            raw.setExternalId(label + "-" + suffix);
            raw.setSourceUrl(url);
            raw.setRawPayload(owner.objectMapper.createObjectNode());
            raw.setPayloadHash(label + "-" + suffix);
            raw.setFetchedAt(now);
            raw.setCreatedAt(now);
            owner.rawItemMapper.insert(raw);

            HotItemEntity item = new HotItemEntity();
            item.setLatestRawItemId(raw.getId());
            item.setSourceType(SourceType.HACKER_NEWS);
            item.setExternalId(label + "-" + suffix);
            item.setItemType("STORY");
            item.setTitle(label + " (arXiv " + ARXIV_ID + ")");
            item.setSourceUrl(url);
            item.setContentHash(label + "-" + suffix);
            item.setFirstSeenAt(now);
            item.setLastSeenAt(now);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            item.setTags(owner.objectMapper.createArrayNode());
            item.setMetrics(owner.objectMapper.createObjectNode());
            owner.hotItemMapper.insert(item);
            return item;
        }

        void addMembership(HotClusterEntity cluster, HotItemEntity item, boolean primary) {
            HotClusterItemEntity membership = new HotClusterItemEntity();
            membership.setHotClusterId(cluster.getId());
            membership.setHotItemId(item.getId());
            membership.setMatchMethod("FIXTURE");
            membership.setMatchScore(BigDecimal.ONE);
            membership.setMatchReason(owner.objectMapper.createObjectNode());
            membership.setRuleVersion("fixture-v1");
            membership.setIsPrimary(primary);
            membership.setAssignedAt(Instant.now());
            owner.clusterItemMapper.insert(membership);
        }
    }
}
