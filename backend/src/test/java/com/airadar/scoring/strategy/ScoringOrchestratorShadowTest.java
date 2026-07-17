package com.airadar.scoring.strategy;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.scoring.entity.HotScoreEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the shadow-mode contract: V2 failure must never block V1.
 */
@ExtendWith(MockitoExtension.class)
class ScoringOrchestratorShadowTest {

    @Mock
    private HnScoreV1Strategy v1Strategy;

    @Mock
    private CrossSourceScoreV2Strategy v2Strategy;

    @Test
    void run_whenV2Fails_returnsV1ResultAndSwallowsException() {
        HotScoreEntity v1Entity = new HotScoreEntity();
        v1Entity.setScoringVersion("hn-score-v1");
        HotClusterEntity cluster = cluster(1L);

        when(v1Strategy.score(cluster)).thenReturn(v1Entity);
        when(v2Strategy.score(cluster)).thenThrow(new RuntimeException("V2 boom"));

        ScoringOrchestrator orchestrator = new ScoringOrchestrator(v1Strategy, v2Strategy);

        HotScoreEntity result = orchestrator.run(cluster);

        assertThat(result).isSameAs(v1Entity);
        verify(v1Strategy).score(cluster);
        verify(v2Strategy).score(cluster);
    }

    @Test
    void run_whenV1Fails_propagatesExceptionAndSkipsV2() {
        HotClusterEntity cluster = cluster(2L);
        when(v1Strategy.score(cluster)).thenThrow(new RuntimeException("V1 boom"));

        ScoringOrchestrator orchestrator = new ScoringOrchestrator(v1Strategy, v2Strategy);

        assertThatThrownBy(() -> orchestrator.run(cluster))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("V1 boom");

        verify(v2Strategy, never()).score(any());
    }

    @Test
    void run_whenBothSucceed_returnsV1Result() {
        HotScoreEntity v1Entity = new HotScoreEntity();
        HotScoreEntity v2Entity = new HotScoreEntity();
        v2Entity.setScoringVersion("cross-source-score-v2");
        HotClusterEntity cluster = cluster(3L);

        when(v1Strategy.score(cluster)).thenReturn(v1Entity);
        when(v2Strategy.score(cluster)).thenReturn(v2Entity);

        ScoringOrchestrator orchestrator = new ScoringOrchestrator(v1Strategy, v2Strategy);

        HotScoreEntity result = orchestrator.run(cluster);

        assertThat(result).isSameAs(v1Entity);
        verify(v2Strategy).score(cluster);
    }

    private HotClusterEntity cluster(long id) {
        HotClusterEntity cluster = new HotClusterEntity();
        cluster.setId(id);
        return cluster;
    }
}
