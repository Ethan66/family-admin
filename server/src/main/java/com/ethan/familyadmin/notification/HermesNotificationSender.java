package com.ethan.familyadmin.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** 只调用固定 Hermes 直投路由；签名使用原始正文，密钥和接口原文不进入错误记录。 */
@Component
@ConditionalOnProperty(prefix="family-admin.course-notifications",name="enabled",havingValue="true")
public class HermesNotificationSender implements AutoCloseable {
    private final ObjectMapper json;
    private final Clock clock;
    private final HttpClient client;

    public HermesNotificationSender(ObjectMapper json, @Qualifier("courseAttendanceClock") Clock clock) {
        this.json=json; this.clock=clock;
        this.client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** 校验直投回执才确认成功；失败由任务层有界重试，不在扣课事务中调用。 */
    public void send(CourseNotificationProperties.Destination target, CourseNotification task) {
        try {
            var body=json.writeValueAsBytes(Map.of("event_type", "UPCOMING".equals(task.getKind()) ? "course.upcoming" : "course.attendance",
                "title",task.getTitle(),"content",task.getContent()));
            var timestamp=Long.toString(clock.instant().getEpochSecond());
            var mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(target.getSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));
            var signature=HexFormat.of().formatHex(mac.doFinal(body));
            var request=HttpRequest.newBuilder(URI.create(target.getUrl())).timeout(Duration.ofSeconds(3))
                .header("Content-Type","application/json").header("X-Request-ID",task.getDedupKey())
                .header("X-Webhook-Timestamp",timestamp).header("X-Webhook-Signature-V2",signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode()!=200) throw new DeliveryException("HTTP_"+response.statusCode(),
                response.statusCode()>=500 || response.statusCode()==429 || response.statusCode()==408);
            // 普通 agent Webhook 的 accepted 不是投递成功，必须使用 deliver_only 的明确回执。
            var result=json.readTree(response.body());
            var status=result==null ? null : result.get("status");
            if (status==null || !status.isTextual() || !Set.of("delivered","duplicate").contains(status.textValue())) {
                throw new DeliveryException("INVALID_RECEIPT",true);
            }
        } catch (HttpTimeoutException error) {
            throw new DeliveryException("TIMEOUT",true);
        } catch (IOException error) {
            throw new DeliveryException("IO_ERROR",true);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new DeliveryException("INTERRUPTED",true);
        } catch (java.security.GeneralSecurityException error) {
            throw new DeliveryException("SIGNATURE_ERROR",false);
        }
    }

    /** 关闭共享连接客户端，不遗留后台网络线程。 */
    @Override @PreDestroy
    public void close() { client.close(); }

    /** 只携带稳定错误分类，禁止把签名、收件会话或网关响应写入日志。 */
    public static class DeliveryException extends RuntimeException {
        private final boolean retryable;
        public DeliveryException(String code, boolean retryable) { super(code); this.retryable=retryable; }
        /** 是否允许按通知任务的最大次数重试。 */
        public boolean retryable() { return retryable; }
    }
}
