package com.airadar.crawl.client.weibo;

public record FetchedWeiboHotTopic(
        String word,
        String note,
        long num,
        String category,
        String mid,
        long rawHot,
        int rank
) {
}
