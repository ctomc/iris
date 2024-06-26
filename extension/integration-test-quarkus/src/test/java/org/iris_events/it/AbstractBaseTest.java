package org.iris_events.it;

import jakarta.inject.Inject;

import org.iris_events.common.message.ErrorMessage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;

public abstract class AbstractBaseTest {

    @Inject
    public ObjectMapper objectMapper;

    public Channel channel;

    public abstract String getErrorMessageQueue();

    protected ErrorMessage getErrorResponse(long maxTimeoutSeconds) throws Exception {
        final int waitTimeoutMillis = 50;
        final var timoutMillis = maxTimeoutSeconds * 1000;
        final int maxRetries = (int) (timoutMillis / waitTimeoutMillis);

        return getErrorResponse(waitTimeoutMillis, 0, maxRetries);
    }

    private ErrorMessage getErrorResponse(int waitTimeoutMillis, final int retries, int maxRetries) throws Exception {
        final var getResponse = channel.basicGet(getErrorMessageQueue(), true);

        if (getResponse != null) {
            return objectMapper.readValue(getResponse.getBody(), ErrorMessage.class);
        }

        if (retries <= maxRetries) {
            final var currentRetry = retries + 1;

            Thread.sleep(waitTimeoutMillis);
            return getErrorResponse(waitTimeoutMillis, currentRetry, maxRetries);
        }

        return null;
    }
}
