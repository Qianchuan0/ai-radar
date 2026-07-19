package com.airadar.item.service;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.strategy.ClusterAssignmentOrchestrator;
import com.airadar.cluster.strategy.ClusterAssignmentResult;
import com.airadar.crawl.model.CrawlStage;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.model.NormalizedHotItem;
import com.airadar.item.normalizer.HotItemNormalizer;
import com.airadar.item.normalizer.HotItemNormalizerRegistry;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.scoring.strategy.ScoringOrchestrator;
import com.airadar.signal.adapter.SourceSignalAdapterRegistry;
import com.airadar.signal.model.NormalizedSignal;
import com.airadar.signal.service.SignalSnapshotService;
import com.airadar.source.entity.SourceConfigEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ItemPipelineService {

    private final HotItemNormalizerRegistry normalizerRegistry;
    private final HotItemService hotItemService;
    private final SourceSignalAdapterRegistry sourceSignalAdapterRegistry;
    private final SignalSnapshotService signalSnapshotService;
    private final ClusterAssignmentOrchestrator clusterOrchestrator;
    private final ScoringOrchestrator scoringOrchestrator;

    public ItemPipelineService(
            HotItemNormalizerRegistry normalizerRegistry,
            HotItemService hotItemService,
            SourceSignalAdapterRegistry sourceSignalAdapterRegistry,
            SignalSnapshotService signalSnapshotService,
            ClusterAssignmentOrchestrator clusterOrchestrator,
            ScoringOrchestrator scoringOrchestrator
    ) {
        this.normalizerRegistry = normalizerRegistry;
        this.hotItemService = hotItemService;
        this.sourceSignalAdapterRegistry = sourceSignalAdapterRegistry;
        this.signalSnapshotService = signalSnapshotService;
        this.clusterOrchestrator = clusterOrchestrator;
        this.scoringOrchestrator = scoringOrchestrator;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(RawItemEntity rawItem, SourceConfigEntity sourceConfig) {
        Optional<NormalizedHotItem> normalized;
        try {
            HotItemNormalizer normalizer = normalizerRegistry.getRequired(sourceConfig.getSourceType());
            normalized = normalizer.normalize(rawItem, sourceConfig);
        } catch (RuntimeException exception) {
            throw new ItemProcessingException(CrawlStage.NORMALIZE, exception);
        }
        if (normalized.isEmpty()) {
            return false;
        }

        HotItemEntity hotItem;
        try {
            hotItem = hotItemService.upsert(rawItem, normalized.get());
        } catch (RuntimeException exception) {
            throw new ItemProcessingException(CrawlStage.PERSIST, exception);
        }

        try {
            NormalizedSignal signal = sourceSignalAdapterRegistry.adapt(hotItem);
            signalSnapshotService.save(rawItem, hotItem, signal);
        } catch (RuntimeException exception) {
            throw new ItemProcessingException(CrawlStage.PERSIST, exception);
        }

        HotClusterEntity cluster;
        try {
            ClusterAssignmentResult assignment = clusterOrchestrator.assign(hotItem);
            cluster = assignment.getCluster();
        } catch (RuntimeException exception) {
            throw new ItemProcessingException(CrawlStage.CLUSTER, exception);
        }

        try {
            scoringOrchestrator.run(cluster);
        } catch (RuntimeException exception) {
            throw new ItemProcessingException(CrawlStage.SCORE, exception);
        }
        return true;
    }
}
