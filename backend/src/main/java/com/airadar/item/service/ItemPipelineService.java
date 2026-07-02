package com.airadar.item.service;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.cluster.service.RuleBasedClusterService;
import com.airadar.crawl.model.CrawlStage;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.item.model.NormalizedHotItem;
import com.airadar.item.normalizer.HackerNewsHotItemNormalizer;
import com.airadar.raw.entity.RawItemEntity;
import com.airadar.scoring.service.RuleBasedScoringService;
import com.airadar.source.entity.SourceConfigEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class ItemPipelineService {

    private final HackerNewsHotItemNormalizer normalizer;
    private final HotItemService hotItemService;
    private final RuleBasedClusterService clusterService;
    private final RuleBasedScoringService scoringService;

    public ItemPipelineService(
            HackerNewsHotItemNormalizer normalizer,
            HotItemService hotItemService,
            RuleBasedClusterService clusterService,
            RuleBasedScoringService scoringService
    ) {
        this.normalizer = normalizer;
        this.hotItemService = hotItemService;
        this.clusterService = clusterService;
        this.scoringService = scoringService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean process(RawItemEntity rawItem, SourceConfigEntity sourceConfig) {
        Optional<NormalizedHotItem> normalized;
        try {
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

        HotClusterEntity cluster;
        try {
            cluster = clusterService.assign(hotItem);
        } catch (RuntimeException exception) {
            throw new ItemProcessingException(CrawlStage.CLUSTER, exception);
        }

        try {
            scoringService.score(cluster);
        } catch (RuntimeException exception) {
            throw new ItemProcessingException(CrawlStage.SCORE, exception);
        }
        return true;
    }
}
