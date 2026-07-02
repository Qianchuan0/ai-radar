package com.airadar.crawl.client.hackernews;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HackerNewsItemResponse(
        Long id,
        Boolean deleted,
        String type,
        String by,
        Long time,
        String text,
        Boolean dead,
        String url,
        Integer score,
        String title,
        Integer descendants,
        List<Long> kids
) {
}
