package org.iris_events.deployment;

import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.jandex.MethodInfo;
import org.objectweb.asm.Opcodes;

import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.smallrye.reactivemessaging.deployment.items.ChannelBuildItem;
import io.quarkus.smallrye.reactivemessaging.deployment.items.ChannelDirection;
import io.quarkus.smallrye.reactivemessaging.deployment.items.ConnectorBuildItem;

public class WiringHelper {

    private WiringHelper() {
        // Avoid direct instantiation
    }

    static void produceIncomingChannel(BuildProducer<ChannelBuildItem> producer, String name) {
        String channelName = normalizeChannelName(name);

        Optional<String> managingConnector = getManagingConnector(ChannelDirection.INCOMING, channelName);
        if (managingConnector.isPresent()) {
            if (isChannelEnabled(ChannelDirection.INCOMING, channelName)) {
                producer.produce(ChannelBuildItem.incoming(channelName, managingConnector.get()));
            }
        } else {
            producer.produce(ChannelBuildItem.incoming(channelName, null));
        }
    }

    static void produceOutgoingChannel(BuildProducer<ChannelBuildItem> producer, String name) {
        String channelName = normalizeChannelName(name);

        Optional<String> managingConnector = getManagingConnector(ChannelDirection.OUTGOING, channelName);
        if (managingConnector.isPresent()) {
            if (isChannelEnabled(ChannelDirection.OUTGOING, channelName)) {
                producer.produce(ChannelBuildItem.outgoing(channelName, managingConnector.get()));
            }
        } else {
            producer.produce(ChannelBuildItem.outgoing(channelName, null));
        }
    }

    /**
     * Gets the name of the connector managing the channel if any.
     * This method looks inside the application configuration.
     *
     * @param direction the direction (incoming or outgoing)
     * @param channel the channel name
     * @return an optional with the connector name if the channel is managed, empty otherwise
     */
    static Optional<String> getManagingConnector(ChannelDirection direction, String channel) {
        return ConfigProvider.getConfig().getOptionalValue(
                "mp.messaging." + direction.name().toLowerCase() + "." + normalizeChannelName(channel) + ".connector",
                String.class);
    }

    /**
     * Checks if the given channel is enabled / disabled in the configuration
     *
     * @param direction the direction (incoming or outgoing)
     * @param channel the channel name
     * @return {@code true} if the channel is enabled, {@code false} otherwise
     */
    static boolean isChannelEnabled(ChannelDirection direction, String channel) {
        return ConfigProvider.getConfig()
                .getOptionalValue(
                        "mp.messaging." + direction.name().toLowerCase() + "." + normalizeChannelName(channel) + ".enabled",
                        Boolean.class)
                .orElse(true);
    }

    /**
     * Normalize the name of a given channel.
     *
     * Concatenate the channel name with double quotes when it contains dots.
     * <p>
     * Otherwise, the SmallRye Reactive Messaging only considers the
     * text up to the first occurrence of a dot as the channel name.
     *
     * @param name the channel name.
     * @return normalized channel name.
     */
    private static String normalizeChannelName(String name) {
        return name != null && !name.startsWith("\"") && name.contains(".") ? "\"" + name + "\"" : name;
    }

    /**
     * Finds a connector by name and direction in the given list.
     *
     * @param connectors the list of connectors
     * @param name the name
     * @param direction the direction
     * @return the found connector, {@code null} otherwise
     */
    static ConnectorBuildItem find(List<ConnectorBuildItem> connectors, String name, ChannelDirection direction) {
        for (ConnectorBuildItem connector : connectors) {
            if (connector.getDirection() == direction && connector.getName().equalsIgnoreCase(name)) {
                return connector;
            }
        }
        return null;
    }

    static boolean hasConnector(List<ConnectorBuildItem> connectors, ChannelDirection direction, String name) {
        return connectors.stream().anyMatch(c -> c.getName().equalsIgnoreCase(name) && c.getDirection() == direction);
    }

    static Class<?> toType(String type) throws ClassNotFoundException {
        switch (type.toLowerCase()) {
            case "boolean":
                return Boolean.class;
            case "int":
                return Integer.class;
            case "string":
                return String.class;
            case "double":
                return Double.class;
            case "float":
                return Float.class;
            case "short":
                return Short.class;
            case "long":
                return Long.class;
            case "byte":
                return Byte.class;
            default:
                return WiringHelper.class.getClassLoader().loadClass(type);
        }
    }

    static boolean isSynthetic(MethodInfo method) {
        short flag = method.flags();
        return (flag & Opcodes.ACC_SYNTHETIC) != 0;
    }

}
