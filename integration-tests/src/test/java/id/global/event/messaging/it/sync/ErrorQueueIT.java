package id.global.event.messaging.it.sync;

import static id.global.event.messaging.runtime.consumer.AmqpConsumer.ERROR_MESSAGE_EXCHANGE;
import static java.util.Collections.emptyMap;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;

import id.global.common.annotations.amqp.Message;
import id.global.common.annotations.amqp.MessageHandler;
import id.global.event.messaging.it.AbstractIntegrationTest;
import id.global.event.messaging.runtime.api.error.ClientError;
import id.global.event.messaging.runtime.api.error.ServerError;
import id.global.event.messaging.runtime.api.exception.BadMessageException;
import id.global.event.messaging.runtime.api.exception.ServerException;
import id.global.event.messaging.runtime.producer.AmqpProducer;
import id.global.event.messaging.runtime.requeue.MessageRequeueHandler;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ErrorQueueIT extends AbstractIntegrationTest {

    private static final String ERROR_QUEUE_BAD_REQUEST = "error-queue-bad-request";
    private static final String ERROR_QUEUE_SERVER_ERROR = "error-queue-server-error";

    @Inject
    AmqpProducer producer;

    @InjectMock
    MessageRequeueHandler requeueHandler;

    @Override
    public String getErrorMessageQueue() {
        return "error-message-error-queue-it";
    }

    @BeforeEach
    void setUp() throws IOException {
        final var connection = rabbitMQClient.connect("ErrorQueueIT publisher");
        channel = connection.createChannel();
        final var errorMessageQueue = getErrorMessageQueue();
        channel.queueDeclare(errorMessageQueue, false, false, false, emptyMap());
        channel.queueBind(errorMessageQueue, ERROR_MESSAGE_EXCHANGE, ERROR_QUEUE_BAD_REQUEST + ".error");
        channel.queueBind(errorMessageQueue, ERROR_MESSAGE_EXCHANGE, ERROR_QUEUE_SERVER_ERROR + ".error");
    }

    @DisplayName("Send bad request error message on corrupted message")
    @Test
    void corruptedMessage() throws Exception {
        final var message = new BadRequestMessage(UUID.randomUUID().toString());

        producer.send(message);

        final var errorMessage = getErrorResponse(5);
        assertThat(errorMessage, is(notNullValue()));
        assertThat(errorMessage.code(), is(ClientError.BAD_REQUEST.getClientCode()));
        assertThat(errorMessage.message(), is("Unable to process message. Message corrupted."));
    }

    @DisplayName("Requeue server error message on server exception")
    @Test
    void serverException() throws Exception {
        final var message = new ServerErrorMessage(UUID.randomUUID().toString());

        producer.send(message);

        final var errorCodeCaptor = ArgumentCaptor.forClass(String.class);
        final var notifyFrontendCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(requeueHandler, timeout(500).times(1))
                .enqueueWithBackoff(any(), errorCodeCaptor.capture(), notifyFrontendCaptor.capture());

        final var errorCode = errorCodeCaptor.getValue();
        final var notifyFrontend = notifyFrontendCaptor.getValue();

        assertThat(errorCode, is(notNullValue()));
        assertThat(errorCode, is(ServerError.INTERNAL_SERVER_ERROR.getClientCode()));
        assertThat(notifyFrontend, CoreMatchers.is(true));
    }

    @SuppressWarnings("unused")
    @ApplicationScoped
    public static class ErrorQueueService {

        @Inject
        public ErrorQueueService() {
        }

        @MessageHandler
        public void handle(BadRequestMessage message) {
            throw new BadMessageException(ClientError.BAD_REQUEST, "Unable to process message. Message corrupted.");
        }

        @MessageHandler
        public void handle(ServerErrorMessage message) {
            throw new ServerException(ServerError.INTERNAL_SERVER_ERROR, "Internal service error.", true);
        }
    }

    @Message(name = ERROR_QUEUE_BAD_REQUEST)
    public record BadRequestMessage(String name) {
    }

    @Message(name = ERROR_QUEUE_SERVER_ERROR)
    public record ServerErrorMessage(String name) {
    }
}
