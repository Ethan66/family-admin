package com.ethan.familyadmin.notification;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

/** 提醒规则与固定通道绑定；配置校验不得打印签名密钥。 */
@Getter @Setter
@ConfigurationProperties(prefix = "family-admin.course-notifications")
public class CourseNotificationProperties {
    /** 默认关闭，避免未配置收件人时自动推送。 */
    private boolean enabled;
    /** 明确的功能启用时点，旧自动记录不补发。 */
    private LocalDateTime enabledFrom;
    /** 提前提醒的分钟数。 */
    private int leadMinutes = 30;
    /** 单轮最多处理的发送任务数。 */
    private int batchSize = 10;
    /** 网络失败允许的最大尝试次数。 */
    private int maxAttempts = 5;
    /** 收件人和 Hermes Webhook 的服务端固定映射。 */
    private List<Destination> destinations = List.of();

    /** 启用时拒绝缺失或冲突配置，禁用时允许保留空通道模板。 */
    @PostConstruct
    public void validate() {
        if (!enabled) return;
        if (enabledFrom == null || leadMinutes < 1 || leadMinutes > 1440 || batchSize < 1 || batchSize > 50
            || maxAttempts < 1 || maxAttempts > 10 || destinations == null || destinations.isEmpty()) {
            throw new IllegalArgumentException("课程通知启用时间、提醒窗口或收件配置不完整");
        }
        var keys = new HashSet<String>();
        var recipients = new HashSet<String>();
        for (var destination : destinations) {
            // 收件人只能从可信配置选择，禁止任意 URL、路径或账号在请求中覆盖。
            if (destination == null || destination.key == null || !destination.key.matches("[A-Za-z0-9_-]{1,32}")
                || destination.familyId == null || destination.familyId <= 0 || destination.userId == null || destination.userId <= 0
                || destination.secret == null || destination.secret.length() < 32 || destination.url == null
                || !keys.add(destination.key) || !recipients.add(destination.familyId + ":" + destination.userId)) {
                throw new IllegalArgumentException("课程通知通道别名、收件人或签名配置无效");
            }
            try {
                var uri = URI.create(destination.url);
                if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().startsWith("/webhooks/")) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("课程通知 Webhook 地址格式无效");
            }
        }
    }

    /** 一位项目收件人对应一个固定微信投递通道；不生成包含密钥的 toString。 */
    @Getter @Setter
    public static class Destination {
        /** 通道别名。 */
        private String key;
        /** 允许推送的家庭。 */
        private Long familyId;
        /** 接收账号编号。 */
        private Long userId;
        /** Hermes 内网 Webhook 地址。 */
        private String url;
        /** HMAC V2 签名密钥。 */
        private String secret;
    }
}
