package com.airadar.cluster.governance;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.entity.HotClusterItemEntity;
import com.airadar.cluster.governance.controller.ClusterGovernanceController;
import com.airadar.cluster.governance.controller.ClusterReviewController;
import com.airadar.cluster.governance.dto.ClusterMergeRequest;
import com.airadar.cluster.governance.dto.ClusterReclusterRequest;
import com.airadar.cluster.governance.dto.ClusterSplitRequest;
import com.airadar.cluster.governance.dto.MoveItemRequest;
import com.airadar.cluster.governance.dto.ReviewResolutionRequest;
import com.airadar.cluster.governance.vo.ClusterMergeResultVO;
import com.airadar.cluster.governance.vo.ClusterReviewTaskVO;
import com.airadar.cluster.governance.vo.ClusterSplitResultVO;
import com.airadar.cluster.governance.vo.MembershipHistoryVO;
import com.airadar.cluster.governance.vo.MoveItemResultVO;
import com.airadar.cluster.governance.vo.ReclusterResultVO;
import com.airadar.cluster.governance.vo.ReviewResolutionVO;
import com.airadar.cluster.mapper.HotClusterItemMapper;
import com.airadar.cluster.mapper.HotClusterMapper;
import com.airadar.cluster.strategy.ClusterMatchDecisionEntity;
import com.airadar.cluster.strategy.ClusterMatchDecisionMapper;
import com.airadar.common.exception.BusinessException;
import com.airadar.common.exception.ErrorCode;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 17B cluster governance integration coverage.
 *
 * <p>Uses Testcontainers + Spring Boot so every governance service runs against
 * a real PostgreSQL with the V10 migration applied. Each test method runs in
 * its own transaction so destructive cleanup between tests is unnecessary.
 */
@Testcontainers
@SpringBootTest(properties = {
        "ai-radar.operations.scheduled-crawl.enabled=false",
        "ai-radar.operations.scheduled-daily-report.enabled=false",
        "ai-radar.cluster.shadow-strategy=event-rule-v2"
})
@Transactional
class ClusterGovernanceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private ClusterGovernanceController governanceController;
    @Autowired
    private ClusterReviewController reviewController;
    @Autowired
    private HotClusterMapper clusterMapper;
    @Autowired
    private HotClusterItemMapper clusterItemMapper;
    @Autowired
    private HotItemMapper hotItemMapper;
    @Autowired
    private ClusterMatchDecisionMapper decisionMapper;
    @Autowired
    private SourceConfigMapper sourceConfigMapper;
    @Autowired
    private CrawlTaskMapper crawlTaskMapper;
    @Autowired
    private RawItemMapper rawItemMapper;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void mergeMovesMembershipsAndMarksLoserMerged() {
        Fixture fixture = Fixture.create(this);
        HotClusterEntity winner = fixture.createCluster("winner");
        HotClusterEntity loser = fixture.createCluster("loser");
        HotItemEntity w1 = fixture.createItem("winner-1", "https://example.com/winner1");
        HotItemEntity l1 = fixture.createItem("loser-1", "https://example.com/loser1");
        HotItemEntity l2 = fixture.createItem("loser-2", "https://example.com/loser2");
        fixture.addMembership(winner, w1, true);
        fixture.addMembership(loser, l1, true);
        fixture.addMembership(loser, l2, false);

        ClusterMergeResultVO result = governanceController.merge(
                winner.getId(),
                new ClusterMergeRequest(loser.getId(), "consolidate duplicate event", "tester")
        ).data();

        assertThat(result.winnerClusterId()).isEqualTo(winner.getId());
        assertThat(result.loserClusterId()).isEqualTo(loser.getId());
        assertThat(result.movedMembershipCount()).isEqualTo(2);

        HotClusterEntity mergedLoser = clusterMapper.selectById(loser.getId());
        assertThat(mergedLoser.getStatus()).isEqualTo("MERGED");
        assertThat(mergedLoser.getMergedIntoClusterId()).isEqualTo(winner.getId());

        List<HotClusterItemEntity> winnerMemberships = activeMembershipsOf(winner.getId());
        assertThat(winnerMemberships).hasSize(3);
        long primaries = winnerMemberships.stream().filter(m -> Boolean.TRUE.equals(m.getIsPrimary())).count();
        assertThat(primaries).isEqualTo(1L);

        List<MembershipHistoryVO> history = governanceController
                .history(winner.getId(), 50).data();
        assertThat(history).hasSize(2);
        assertThat(history).allMatch(h -> "MERGE".equals(h.action()));
        assertThat(history).allMatch(h -> winner.getId().equals(h.hotClusterId()));
        assertThat(history).allMatch(h -> loser.getId().equals(h.fromClusterId()));
        assertThat(history).allMatch(h -> winner.getId().equals(h.toClusterId()));
    }

    @Test
    void mergeRejectsSelfMerge() {
        Fixture fixture = Fixture.create(this);
        HotClusterEntity cluster = fixture.createCluster("solo");
        HotItemEntity item = fixture.createItem("only", "https://example.com/only");
        fixture.addMembership(cluster, item, true);

        assertThatThrownBy(() -> governanceController.merge(
                cluster.getId(),
                new ClusterMergeRequest(cluster.getId(), "self-merge", "tester")
        ))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT);
    }

    @Test
    void mergeRejectsAlreadyMergedLoser() {
        Fixture fixture = Fixture.create(this);
        HotClusterEntity winner = fixture.createCluster("winner");
        HotClusterEntity firstLoser = fixture.createCluster("firstLoser");
        HotClusterEntity secondLoser = fixture.createCluster("secondLoser");
        HotItemEntity w = fixture.createItem("w", "https://example.com/w");
        HotItemEntity f = fixture.createItem("f", "https://example.com/f");
        HotItemEntity s = fixture.createItem("s", "https://example.com/s");
        fixture.addMembership(winner, w, true);
        fixture.addMembership(firstLoser, f, true);
        fixture.addMembership(secondLoser, s, true);

        governanceController.merge(winner.getId(),
                new ClusterMergeRequest(firstLoser.getId(), "first", "tester"));

        assertThatThrownBy(() -> governanceController.merge(
                firstLoser.getId(),
                new ClusterMergeRequest(secondLoser.getId(), "cascade", "tester")
        ))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET);
    }

    @Test
    void splitCreatesNewClusterAndPreservesPrimarySelection() {
        Fixture fixture = Fixture.create(this);
        HotClusterEntity source = fixture.createCluster("source");
        HotItemEntity keep = fixture.createItem("keep", "https://example.com/keep");
        HotItemEntity leave1 = fixture.createItem("leave1", "https://example.com/leave1");
        HotItemEntity leave2 = fixture.createItem("leave2", "https://example.com/leave2");
        fixture.addMembership(source, keep, true);
        fixture.addMembership(source, leave1, false);
        fixture.addMembership(source, leave2, false);

        ClusterSplitResultVO result = governanceController.split(
                source.getId(),
                new ClusterSplitRequest(List.of(leave1.getId(), leave2.getId()), null, "different event", "tester")
        ).data();

        assertThat(result.sourceClusterId()).isEqualTo(source.getId());
        assertThat(result.targetCreated()).isTrue();
        assertThat(result.movedItemIds())
                .containsExactlyInAnyOrder(leave1.getId(), leave2.getId());

        HotClusterEntity target = clusterMapper.selectById(result.targetClusterId());
        assertThat(target.getStatus()).isEqualTo("ACTIVE");

        assertThat(activeMembershipsOf(source.getId()))
                .extracting(HotClusterItemEntity::getHotItemId)
                .containsExactly(keep.getId());
        assertThat(activeMembershipsOf(result.targetClusterId()))
                .extracting(HotClusterItemEntity::getHotItemId)
                .containsExactlyInAnyOrder(leave1.getId(), leave2.getId());

        long targetPrimaries = activeMembershipsOf(result.targetClusterId()).stream()
                .filter(m -> Boolean.TRUE.equals(m.getIsPrimary()))
                .count();
        assertThat(targetPrimaries).isEqualTo(1L);
        HotClusterEntity refreshedSource = clusterMapper.selectById(source.getId());
        assertThat(refreshedSource.getPrimaryItemId()).isEqualTo(keep.getId());
        HotClusterEntity refreshedTarget = clusterMapper.selectById(result.targetClusterId());
        assertThat(refreshedTarget.getPrimaryItemId()).isIn(leave1.getId(), leave2.getId());
    }

    @Test
    void splitRejectsLeavingSourceEmpty() {
        Fixture fixture = Fixture.create(this);
        HotClusterEntity source = fixture.createCluster("source");
        HotClusterEntity target = fixture.createCluster("target");
        HotItemEntity i1 = fixture.createItem("i1", "https://example.com/i1");
        HotItemEntity i2 = fixture.createItem("i2", "https://example.com/i2");
        fixture.addMembership(source, i1, true);
        fixture.addMembership(source, i2, false);

        assertThatThrownBy(() -> governanceController.split(
                source.getId(),
                new ClusterSplitRequest(List.of(i1.getId(), i2.getId()), target.getId(), "empty", "tester")
        ))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.CLUSTER_GOVERNANCE_INVALID_ARGUMENT);
    }

    @Test
    void moveRelocatesSingleItemAndReselectsBothActiveClusters() {
        Fixture fixture = Fixture.create(this);
        HotClusterEntity a = fixture.createCluster("a");
        HotClusterEntity b = fixture.createCluster("b");
        HotItemEntity aItem = fixture.createItem("aItem", "https://example.com/a");
        HotItemEntity aKeep = fixture.createItem("aKeep", "https://example.com/a-keep");
        HotItemEntity bItem = fixture.createItem("bItem", "https://example.com/b");
        fixture.addMembership(a, aItem, true);
        fixture.addMembership(a, aKeep, false);
        fixture.addMembership(b, bItem, true);

        MoveItemResultVO result = governanceController.move(
                a.getId(),
                aItem.getId(),
                new MoveItemRequest(b.getId(), "better fit", "tester")
        ).data();

        assertThat(result.fromClusterId()).isEqualTo(a.getId());
        assertThat(result.toClusterId()).isEqualTo(b.getId());
        assertThat(result.hotItemId()).isEqualTo(aItem.getId());
        assertThat(activeMembershipsOf(a.getId()))
                .extracting(HotClusterItemEntity::getHotItemId)
                .containsExactly(aKeep.getId());
        assertThat(activeMembershipsOf(b.getId()))
                .extracting(HotClusterItemEntity::getHotItemId)
                .containsExactlyInAnyOrder(aItem.getId(), bItem.getId());
        HotClusterEntity refreshedA = clusterMapper.selectById(a.getId());
        assertThat(refreshedA.getStatus()).isEqualTo("ACTIVE");
        assertThat(refreshedA.getPrimaryItemId()).isEqualTo(aKeep.getId());
        HotClusterEntity refreshedB = clusterMapper.selectById(b.getId());
        assertThat(refreshedB.getPrimaryItemId()).isIn(aItem.getId(), bItem.getId());
    }

    @Test
    void moveClosesSourceClusterWhenLastMemberMovesOut() {
        Fixture fixture = Fixture.create(this);
        HotClusterEntity a = fixture.createCluster("a");
        HotClusterEntity b = fixture.createCluster("b");
        HotItemEntity aItem = fixture.createItem("aItem", "https://example.com/a-last");
        HotItemEntity bItem = fixture.createItem("bItem", "https://example.com/b-last");
        fixture.addMembership(a, aItem, true);
        fixture.addMembership(b, bItem, true);

        MoveItemResultVO result = governanceController.move(
                a.getId(),
                aItem.getId(),
                new MoveItemRequest(b.getId(), "singleton should collapse into target", "tester")
        ).data();

        assertThat(result.fromClusterId()).isEqualTo(a.getId());
        assertThat(activeMembershipsOf(a.getId())).isEmpty();
        HotClusterEntity closedSource = clusterMapper.selectById(a.getId());
        assertThat(closedSource.getStatus()).isEqualTo("MERGED");
        assertThat(closedSource.getMergedIntoClusterId()).isEqualTo(b.getId());
        assertThat(closedSource.getPrimaryItemId()).isNull();
        assertThat(activeMembershipsOf(b.getId()))
                .extracting(HotClusterItemEntity::getHotItemId)
                .containsExactlyInAnyOrder(aItem.getId(), bItem.getId());
    }

    @Test
    void moveRejectsWhenItemIsNotAnActiveMember() {
        Fixture fixture = Fixture.create(this);
        HotClusterEntity a = fixture.createCluster("a");
        HotClusterEntity b = fixture.createCluster("b");
        HotItemEntity item = fixture.createItem("item", "https://example.com/item");

        assertThatThrownBy(() -> governanceController.move(
                a.getId(),
                item.getId(),
                new MoveItemRequest(b.getId(), "missing", "tester")
        ))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.CLUSTER_GOVERNANCE_NO_MEMBERSHIP);
    }

    @Test
    void reclusterPersistsShadowDecisionsAndHistory() {
        Fixture fixture = Fixture.create(this);
        HotClusterEntity source = fixture.createCluster("source");
        HotItemEntity item = fixture.createItem("reclustered", "https://example.com/reclustered");
        fixture.addMembership(source, item, true);

        ReclusterResultVO result = governanceController.recluster(
                source.getId(),
                new ClusterReclusterRequest(List.of(item.getId()), "verify", "tester")
        ).data();

        assertThat(result.evaluatedItemCount()).isEqualTo(1);
        assertThat(result.historyIds()).hasSize(1);
        assertThat(activeMembershipsOf(source.getId()))
                .extracting(HotClusterItemEntity::getHotItemId)
                .containsExactly(item.getId());

        List<ClusterMatchDecisionEntity> decisions = decisionMapper.selectList(
                new LambdaQueryWrapper<ClusterMatchDecisionEntity>()
                        .eq(ClusterMatchDecisionEntity::getHotItemId, item.getId()));
        assertThat(decisions).isNotEmpty();
        List<MembershipHistoryVO> history = governanceController
                .history(source.getId(), 10).data();
        assertThat(history).extracting(MembershipHistoryVO::action).contains("RECLUSTER");
    }

    @Test
    void reviewQueueMaterializesOpenTasksAndAcceptMovesItem() {
        Fixture fixture = Fixture.create(this);
        HotClusterEntity candidate = fixture.createCluster("candidate");
        HotClusterEntity current = fixture.createCluster("current");
        HotItemEntity item = fixture.createItem("reviewed", "https://example.com/reviewed");
        fixture.addMembership(current, item, true);

        ClusterMatchDecisionEntity decision = new ClusterMatchDecisionEntity();
        decision.setHotItemId(item.getId());
        decision.setCandidateClusterId(candidate.getId());
        decision.setDecision("REVIEW_REQUIRED");
        decision.setMatchScore(new BigDecimal("0.70"));
        decision.setMatchMethod("SIMILARITY");
        decision.setMatchReason(objectMapper.createObjectNode());
        decision.setRuleVersion("event-rule-v2");
        decision.setDecidedAt(Instant.now());
        decisionMapper.insert(decision);

        var page = reviewController.list("OPEN", 1, 20).data();
        assertThat(page.items()).extracting(ClusterReviewTaskVO::clusterMatchDecisionId)
                .contains(decision.getId());
        Long taskId = page.items().stream()
                .filter(t -> decision.getId().equals(t.clusterMatchDecisionId()))
                .map(ClusterReviewTaskVO::id)
                .findFirst()
                .orElseThrow();

        ReviewResolutionVO accepted = reviewController.accept(
                taskId,
                new ReviewResolutionRequest("confirmed merge", "tester")
        ).data();

        assertThat(accepted.status()).isEqualTo("ACCEPTED");
        assertThat(accepted.membershipHistoryId()).isNotNull();
        assertThat(activeMembershipsOf(candidate.getId()))
                .extracting(HotClusterItemEntity::getHotItemId)
                .contains(item.getId());
        assertThat(activeMembershipsOf(current.getId())).isEmpty();
        HotClusterEntity closedCurrent = clusterMapper.selectById(current.getId());
        assertThat(closedCurrent.getStatus()).isEqualTo("MERGED");
        assertThat(closedCurrent.getMergedIntoClusterId()).isEqualTo(candidate.getId());
        assertThat(closedCurrent.getPrimaryItemId()).isNull();

        assertThatThrownBy(() -> reviewController.accept(
                taskId,
                new ReviewResolutionRequest("again", "tester")
        ))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.CLUSTER_GOVERNANCE_INVALID_TARGET);
    }

    @Test
    void reviewRejectLeavesMembershipUntouched() {
        Fixture fixture = Fixture.create(this);
        HotClusterEntity candidate = fixture.createCluster("candidate");
        HotClusterEntity current = fixture.createCluster("current");
        HotItemEntity item = fixture.createItem("rejected", "https://example.com/rejected");
        fixture.addMembership(current, item, true);

        ClusterMatchDecisionEntity decision = new ClusterMatchDecisionEntity();
        decision.setHotItemId(item.getId());
        decision.setCandidateClusterId(candidate.getId());
        decision.setDecision("REVIEW_REQUIRED");
        decision.setMatchScore(new BigDecimal("0.65"));
        decision.setMatchMethod("SIMILARITY");
        decision.setMatchReason(objectMapper.createObjectNode());
        decision.setRuleVersion("event-rule-v2");
        decision.setDecidedAt(Instant.now());
        decisionMapper.insert(decision);

        var page = reviewController.list("OPEN", 1, 20).data();
        Long taskId = page.items().stream()
                .filter(t -> decision.getId().equals(t.clusterMatchDecisionId()))
                .map(ClusterReviewTaskVO::id)
                .findFirst()
                .orElseThrow();

        ReviewResolutionVO rejected = reviewController.reject(
                taskId,
                new ReviewResolutionRequest("wrong candidate", "tester")
        ).data();
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.membershipHistoryId()).isNull();
        assertThat(activeMembershipsOf(current.getId()))
                .extracting(HotClusterItemEntity::getHotItemId)
                .containsExactly(item.getId());
        assertThat(activeMembershipsOf(candidate.getId())).isEmpty();
    }

    private List<HotClusterItemEntity> activeMembershipsOf(long clusterId) {
        return clusterItemMapper.selectList(
                new LambdaQueryWrapper<HotClusterItemEntity>()
                        .eq(HotClusterItemEntity::getHotClusterId, clusterId)
                        .isNull(HotClusterItemEntity::getRemovedAt)
        );
    }

    /**
     * Minimal fixture that creates the full source_config -> crawl_task ->
     * raw_item -> hot_item FK chain so governance services can manipulate
     * hot_cluster / hot_cluster_item rows without violating schema.
     */
    static final class Fixture {
        private final ClusterGovernanceIntegrationTest owner;
        private final String suffix = UUID.randomUUID().toString().substring(0, 8);
        private SourceConfigEntity sharedSourceConfig;
        private CrawlTaskEntity sharedCrawlTask;

        private Fixture(ClusterGovernanceIntegrationTest owner) {
            this.owner = owner;
        }

        static Fixture create(ClusterGovernanceIntegrationTest owner) {
            Fixture f = new Fixture(owner);
            f.seedSource();
            return f;
        }

        private void seedSource() {
            Instant now = Instant.now();
            SourceConfigEntity source = new SourceConfigEntity();
            source.setSourceCode("gov-test-" + suffix);
            source.setSourceType(SourceType.HACKER_NEWS);
            source.setDisplayName("Governance test " + suffix);
            source.setEnabled(Boolean.TRUE);
            source.setConfigPayload(owner.objectMapper.createObjectNode());
            source.setVersion(0);
            source.setCreatedAt(now);
            source.setUpdatedAt(now);
            owner.sourceConfigMapper.insert(source);
            this.sharedSourceConfig = source;

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
            this.sharedCrawlTask = task;
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
            raw.setCrawlTaskId(sharedCrawlTask.getId());
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
