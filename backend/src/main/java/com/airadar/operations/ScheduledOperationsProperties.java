package com.airadar.operations;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-radar.operations")
public class ScheduledOperationsProperties {

    private final ScheduledCrawl scheduledCrawl = new ScheduledCrawl();

    public ScheduledCrawl getScheduledCrawl() {
        return scheduledCrawl;
    }

    public static class ScheduledCrawl {

        private boolean enabled = false;
        private Duration fixedDelay = Duration.ofSeconds(60);
        private Duration initialDelay = Duration.ofSeconds(30);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getFixedDelay() {
            return fixedDelay;
        }

        public void setFixedDelay(Duration fixedDelay) {
            this.fixedDelay = fixedDelay;
        }

        public Duration getInitialDelay() {
            return initialDelay;
        }

        public void setInitialDelay(Duration initialDelay) {
            this.initialDelay = initialDelay;
        }
    }
}
