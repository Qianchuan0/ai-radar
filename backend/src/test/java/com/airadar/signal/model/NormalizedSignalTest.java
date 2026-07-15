package com.airadar.signal.model;

import com.airadar.source.model.SourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NormalizedSignalTest {

    @Test
    void shouldExposeSocialSignalAndRankSemantics() {
        NormalizedSignal communitySignal = NormalizedSignal.of(
            SourceType.HACKER_NEWS,
            SourceRole.COMMUNITY,
            12.5,
            7.5,
            0.0,
            null
        );

        assertThat(communitySignal.totalSocialSignal()).isEqualTo(20.0);
        assertThat(communitySignal.getRank()).isEmpty();
        assertThat(communitySignal.isSearchResult()).isFalse();

        NormalizedSignal searchSignal = NormalizedSignal.ofSearchResult(
            SourceType.BING_SEARCH,
            SourceRole.DISCOVERY,
            90.0,
            2,
            null
        );

        assertThat(searchSignal.totalSocialSignal()).isZero();
        assertThat(searchSignal.getRank()).contains(2);
        assertThat(searchSignal.isSearchResult()).isTrue();
    }
}
