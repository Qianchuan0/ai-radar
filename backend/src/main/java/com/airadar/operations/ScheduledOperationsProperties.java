package com.airadar.operations;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ai-radar.operations")
public class ScheduledOperationsProperties {

    private final ScheduledCrawl scheduledCrawl = new ScheduledCrawl();
    private final ScheduledDailyReport scheduledDailyReport = new ScheduledDailyReport();

    public ScheduledCrawl getScheduledCrawl() {
        return scheduledCrawl;
    }

    public ScheduledDailyReport getScheduledDailyReport() {
        return scheduledDailyReport;
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

    public static class ScheduledDailyReport {

        private boolean enabled = false;
        private Duration fixedDelay = Duration.ofMinutes(15);
        private Duration initialDelay = Duration.ofMinutes(1);
        private int reportDateOffsetDays = 1;
        private boolean refreshExisting = false;

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

        public int getReportDateOffsetDays() {
            return reportDateOffsetDays;
        }

        public void setReportDateOffsetDays(int reportDateOffsetDays) {
            this.reportDateOffsetDays = reportDateOffsetDays;
        }

        public boolean isRefreshExisting() {
            return refreshExisting;
        }

        public void setRefreshExisting(boolean refreshExisting) {
            this.refreshExisting = refreshExisting;
        }
    }
}
