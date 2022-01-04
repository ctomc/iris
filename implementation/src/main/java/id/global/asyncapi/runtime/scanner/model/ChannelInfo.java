package id.global.asyncapi.runtime.scanner.model;

import java.util.Set;

import id.global.common.auth.jwt.Role;

public class ChannelInfo {

    private final String eventKey;
    private final ChannelBindingsInfo bindingsInfo;
    private final String operationType;
    private final Set<Role> rolesAllowed;
    private final String deadLetterQueue;
    private final Integer ttl;

    public ChannelInfo(String eventKey, ChannelBindingsInfo bindingsInfo, String operationType, Set<Role> rolesAllowed,
            String deadLetterQueue, Integer ttl) {
        this.eventKey = eventKey;
        this.bindingsInfo = bindingsInfo;
        this.operationType = operationType;
        this.rolesAllowed = rolesAllowed;
        this.deadLetterQueue = deadLetterQueue;
        this.ttl = ttl;
    }

    public String getEventKey() {
        return eventKey;
    }

    public ChannelBindingsInfo getBindingsInfo() {
        return bindingsInfo;
    }

    public String getOperationType() {
        return operationType;
    }

    public Set<Role> getRolesAllowed() {
        return rolesAllowed;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public Integer getTtl() {
        return ttl;
    }
}
