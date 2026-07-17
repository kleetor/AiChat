package com.example.aichat.config.props;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "summary")
public class SummaryProperties {
    private Trigger trigger = new Trigger();
    private Refresh refresh = new Refresh();
    private Keep keep = new Keep();

    @Data
    public static class Trigger {
        private int count = 20;
    }

    @Data
    public static class Refresh {
        private int interval = 10;
    }

    @Data
    public static class Keep {
        private int recent = 10;
    }
}
