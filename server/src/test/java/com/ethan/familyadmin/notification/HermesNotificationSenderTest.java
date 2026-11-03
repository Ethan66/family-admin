package com.ethan.familyadmin.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

/** 本地 HTTP 服务验证真实签名和回执，不连接微信或生产网关。 */
class HermesNotificationSenderTest {
    @Test void signsExactPayloadAndRequiresADeliveredReceipt() throws Exception {
        var body = new AtomicReference<byte[]>();
        var signature = new AtomicReference<String>();
        var requestId = new AtomicReference<String>();
        var response = new AtomicReference<>("{\"status\":\"delivered\"}");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/webhooks/family-course", exchange -> {
            body.set(exchange.getRequestBody().readAllBytes());
            signature.set(exchange.getRequestHeaders().getFirst("X-Webhook-Signature-V2"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
            var bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var clock = Clock.fixed(Instant.parse("2026-11-03T09:30:00Z"),ZoneOffset.UTC);
        try (var sender = new HermesNotificationSender(new ObjectMapper(),clock)) {
            var target = CourseNotificationServiceTest.properties().getDestinations().getFirst();
            target.setUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/webhooks/family-course");
            var task = new CourseNotification(); task.setDedupKey("ATTENDANCE:8:2"); task.setKind("ATTENDANCE");
            task.setTitle("扣课结果"); task.setContent("英语：2/12 课时");
            sender.send(target,task);
            var mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(target.getSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            mac.update((clock.instant().getEpochSecond()+".").getBytes(StandardCharsets.UTF_8));
            assertThat(signature.get()).isEqualTo(HexFormat.of().formatHex(mac.doFinal(body.get())));
            assertThat(requestId.get()).isEqualTo(task.getDedupKey());
            assertThat(new String(body.get(),StandardCharsets.UTF_8)).contains("英语：2/12");
            response.set("{\"status\":\"duplicate\"}"); sender.send(target,task);
            response.set("{\"status\":\"accepted\"}");
            assertThatThrownBy(() -> sender.send(target,task)).isInstanceOf(HermesNotificationSender.DeliveryException.class);
        } finally { server.stop(0); }
    }
}
