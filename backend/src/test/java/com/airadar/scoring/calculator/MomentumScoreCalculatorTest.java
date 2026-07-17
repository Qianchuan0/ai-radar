package com.airadar.scoring.calculator;

import com.airadar.cluster.entity.HotClusterEntity;
import com.airadar.item.entity.HotItemEntity;
import com.airadar.scoring.strategy.ScoringContext;
import com.airadar.scoring.strategy.model.ScoreComponent;
import com.airadar.signal.model.GrowthConfidence;
import com.airadar.signal.model.GrowthMetrics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MomentumScoreCalculatorTest {

    private final MomentumScoreCalculator calculator = new MomentumScoreCalculator();

    @Test
    void compute_withHighConfidence_keepsFullScore() {
        HotItemEntity primary = primaryItem(1L);
        GrowthMetrics growth = new GrowthMetrics(
                1L, "24h", 30.0, 6.0, 60.0, 0.0, null, 80.0, GrowthConfidence.HIGH);
        ScoringContext context = context(primary, Map.of(1L, growth));

        ScoreComponent result = calculator.compute(context);

        assertThat(result.name()).isEqualTo("momentum");
        assertThat(result.weight()).isEqualTo(0.25);
        assertThat(result.score()).isEqualTo(80.0);
        assertThat(result.reasons()).anyMatch(r -> r.contains("confidence=HIGH"));
    }

    @Test
    void compute_withUnknownConfidence_attenuatesScore() {
        HotItemEntity primary = primaryItem(1L);
        GrowthMetrics growth = new GrowthMetrics(
                1L, "24h", 30.0, 6.0, 60.0, 0.0, null, 80.0, GrowthConfidence.UNKNOWN);
        ScoringContext context = context(primary, Map.of(1L, growth));

        ScoreComponent result = calculator.compute(context);

        // 80 * 0.3 = 24
        assertThat(result.score()).isEqualTo(24.0);
    }

    @Test
    void compute_withNullMomentum_returnsLowBaseline() {
        HotItemEntity primary = primaryItem(1L);
        GrowthMetrics growth = new GrowthMetrics(
                1L, "24h", null, null, null, null, null, null, GrowthConfidence.UNKNOWN);
        ScoringContext context = context(primary, Map.of(1L, growth));

        ScoreComponent result = calculator.compute(context);

        assertThat(result.score()).isEqualTo(10.0);
    }

    @Test
    void compute_withoutGrowthData_returnsLowBaseline() {
        HotItemEntity primary = primaryItem(1L);
        ScoringContext context = context(primary, Map.of());

        ScoreComponent result = calculator.compute(context);

        assertThat(result.score()).isEqualTo(10.0);
        assertThat(result.reasons()).contains("no_growth_data_for_primary_item");
    }

    private HotItemEntity primaryItem(long id) {
        HotItemEntity item = new HotItemEntity();
        item.setId(id);
        return item;
    }

    private ScoringContext context(HotItemEntity primary, Map<Long, GrowthMetrics> growth) {
        return new ScoringContext(
                new HotClusterEntity(),
                List.of(primary),
                primary,
                Map.of(),
                growth,
                Instant.parse("2026-07-17T12:00:00Z")
        );
    }
}
