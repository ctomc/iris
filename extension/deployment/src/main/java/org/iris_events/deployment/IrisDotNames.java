package org.iris_events.deployment;

import java.util.concurrent.CompletionStage;

import jakarta.enterprise.context.Dependent;

import org.eclipse.microprofile.reactive.messaging.Acknowledgment;
import org.iris_events.annotations.CachedMessage;
import org.iris_events.annotations.IrisGenerated;
import org.iris_events.annotations.Message;
import org.iris_events.annotations.MessageHandler;
import org.iris_events.common.message.SnapshotRequested;
import org.jboss.jandex.DotName;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.reactive.messaging.annotations.Blocking;

public final class IrisDotNames {
    public static final DotName MESSAGE = DotName.createSimple(Message.class.getCanonicalName());
    public static final DotName DOT_NAME_IRIS_GENERATED = DotName.createSimple(IrisGenerated.class.getCanonicalName());

    public static final DotName DOT_NAME_PRODUCED_EVENT = DotName.createSimple(Message.class.getCanonicalName());
    public static final DotName DOT_NAME_CACHED_MESSAGE = DotName.createSimple(CachedMessage.class.getCanonicalName());
    public static final DotName MESSAGE_HANDLER = DotName.createSimple(MessageHandler.class.getCanonicalName());
    public static final DotName SNAPSHOT_REQUESTED_DOT_NAME = DotName.createSimple(SnapshotRequested.class.getCanonicalName());

    static final DotName BLOCKING = DotName.createSimple(Blocking.class.getName());
    static final DotName SMALLRYE_BLOCKING = DotName.createSimple(io.smallrye.common.annotation.Blocking.class.getName());
    static final DotName TRANSACTIONAL = DotName.createSimple("jakarta.transaction.Transactional");
    static final DotName RUN_ON_VIRTUAL_THREAD = DotName.createSimple(RunOnVirtualThread.class.getName());
    static final DotName KOTLIN_UNIT = DotName.createSimple("kotlin.Unit");
    static final DotName VOID = DotName.createSimple(void.class.getName());

    static final DotName VOID_CLASS = DotName.createSimple(Void.class.getName());
    static final DotName OBJECT = DotName.createSimple(Object.class.getName());
    static final DotName COMPLETION_STAGE = DotName.createSimple(CompletionStage.class.getName());

    static final DotName ABSTRACT_SUBSCRIBING_COROUTINE_INVOKER = DotName
            .createSimple("io.quarkus.smallrye.reactivemessaging.runtime.kotlin.AbstractSubscribingCoroutineInvoker");
    static final DotName CONTINUATION = DotName.createSimple("kotlin.coroutines.Continuation");
    static final DotName ACKNOWLEDGMENT = DotName.createSimple(Acknowledgment.class.getName());
    static final DotName DEPENDENT = DotName.createSimple(Dependent.class);

}
