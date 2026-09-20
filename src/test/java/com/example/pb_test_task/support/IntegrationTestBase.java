package com.example.pb_test_task.support;

import com.example.pb_test_task.api.dto.CreateOrderRequest;
import com.example.pb_test_task.api.dto.OrderResponse;
import com.example.pb_test_task.config.OrderProperties;
import com.example.pb_test_task.domain.ClientDailyLimit;
import com.example.pb_test_task.processing.OrderProcessingListener;
import com.example.pb_test_task.repository.ClientDailyLimitRepository;
import com.example.pb_test_task.repository.OrderRepository;
import com.example.pb_test_task.repository.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import static java.math.BigDecimal.ZERO;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import({TestcontainersConfiguration.class, IntegrationTestConfig.class})
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    protected OrderProperties properties;

    @Autowired
    protected ScriptedPaymentProvider provider;

    @Autowired
    protected OrderRepository orderRepository;

    @Autowired
    protected OutboxMessageRepository outboxRepository;

    @Autowired
    protected ClientDailyLimitRepository limitRepository;

    @Autowired
    protected Clock clock;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ControllableRabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    private RestClient restClient;

    @BeforeEach
    void initRestClient() {
        restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    protected ApiResult<OrderResponse> createOrder(UUID idempotencyKey, UUID clientId, BigDecimal amount) {
        return restClient.post()
                .uri("/api/v1/orders")
                .header("X-Idempotency-Key", idempotencyKey.toString())
                .contentType(APPLICATION_JSON)
                .body(new CreateOrderRequest(clientId, amount))
                .exchange((_, response) -> ApiResult.from(response, OrderResponse.class), false);
    }

    protected ApiResult<OrderResponse> createOrderWithoutIdempotencyKey(UUID clientId, BigDecimal amount) {
        return restClient.post()
                .uri("/api/v1/orders")
                .contentType(APPLICATION_JSON)
                .body(new CreateOrderRequest(clientId, amount))
                .exchange((_, response) -> ApiResult.from(response, OrderResponse.class), false);
    }

    protected ApiResult<OrderResponse> getOrder(UUID orderId) {
        return restClient.get()
                .uri("/api/v1/orders/{id}", orderId)
                .exchange((_, response) -> ApiResult.from(response, OrderResponse.class), false);
    }

    protected ApiResult<OrderResponse> cancelOrder(UUID orderId) {
        return restClient.post()
                .uri("/api/v1/orders/{id}/cancel", orderId)
                .exchange((_, response) -> ApiResult.from(response, OrderResponse.class), false);
    }

    protected void withProcessingPaused(Runnable action) {
        withListenerPaused(OrderProcessingListener.LISTENER_ID, action);
    }

    protected void withListenerPaused(String listenerId, Runnable action) {
        var container = listenerRegistry.getListenerContainer(listenerId);
        container.stop();
        try {
            action.run();
        } finally {
            container.start();
        }
    }

    /** Discards whatever is sitting on a queue */
    protected void drainQueue(String queueName) {
        while (rabbitTemplate.receive(queueName, 200) != null) {
            // nothing to do, the point is to empty it
        }
    }

    protected BigDecimal reservedToday(UUID clientId) {
        return limitRepository.findById(new ClientDailyLimit.Key(clientId, today()))
                .map(ClientDailyLimit::getReserved)
                .orElse(ZERO);
    }

    protected LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), properties.timeZone());
    }

    public record ApiResult<T>(HttpStatusCode status, T body) {

        static <T> ApiResult<T> from(ConvertibleClientHttpResponse response, Class<T> type) throws IOException {
            HttpStatusCode status = response.getStatusCode();
            return new ApiResult<>(status, status.is2xxSuccessful() ? response.bodyTo(type) : null);
        }

        public T require() {
            if (body == null) {
                throw new AssertionError("Expected a body but the request returned " + status);
            }
            return body;
        }
    }
}
