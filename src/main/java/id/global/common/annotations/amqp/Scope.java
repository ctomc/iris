package id.global.common.annotations.amqp;

public enum Scope {
    /**
     * Backend inter-service message
     */
    INTERNAL,
    /**
     * Request message on websocket from client/frontend
     */
    FRONTEND,
    /**
     * Message intended for user on all his sessions
     */
    USER,
    /**
     * Message intended for user on exact session
     */
    SESSION,
    /**
     * Message intended for all users on all sessions
     */
    BROADCAST
}
