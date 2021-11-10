package id.global.event.messaging.runtime.channel;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import id.global.event.messaging.runtime.configuration.AmqpConfiguration;
import id.global.event.messaging.runtime.connection.ProducerConnectionProvider;

@ApplicationScoped
public class ProducerChannelService extends AbstractChannelService {
    @Inject
    public ProducerChannelService(ProducerConnectionProvider connectionProvider, AmqpConfiguration configuration) {
        super(connectionProvider, configuration);
    }
}