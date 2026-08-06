package com.fowoco.server.airun.api;

import com.fowoco.server.airun.application.AiRunPublicEvent;
import com.fowoco.server.airun.application.AiRunResult;
import com.fowoco.server.airun.application.error.AiRunErrorCode;
import com.fowoco.server.airun.application.port.AiRunPublicEventPublisher;
import com.fowoco.server.auth.application.ActorContext;
import com.fowoco.server.common.error.ApiException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@EnableConfigurationProperties(AiRunEventStreamProperties.class)
public class AiRunEventStreamBroker implements AiRunPublicEventPublisher {

    private final AiRunEventStreamProperties properties;
    private final Clock clock;
    private final Map<RunKey, EventHistory> histories = new ConcurrentHashMap<>();
    private final Map<SubscriptionKey, CopyOnWriteArrayList<StreamSession>> subscriptions =
            new ConcurrentHashMap<>();

    public AiRunEventStreamBroker(AiRunEventStreamProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void publish(UUID companyId, AiRunResult run) {
        RunKey runKey = new RunKey(companyId, run.aiRunId());
        AiRunPublicEvent event = AiRunPublicEvent.from(run);
        histories.computeIfAbsent(runKey, ignored -> new EventHistory())
                .record(event, clock.instant(), properties.historySize());

        subscriptions.forEach((key, sessions) -> {
            if (key.companyId().equals(companyId) && key.aiRunId().equals(run.aiRunId())) {
                sessions.forEach(session -> send(session, key, event));
            }
        });
    }

    public SseEmitter subscribe(ActorContext actor, AiRunResult current, String lastEventIdHeader) {
        Long lastEventId = parseLastEventId(lastEventIdHeader);
        RunKey runKey = new RunKey(actor.companyId(), current.aiRunId());
        SubscriptionKey subscriptionKey = new SubscriptionKey(
                actor.companyId(),
                actor.actorId(),
                current.aiRunId()
        );
        CopyOnWriteArrayList<StreamSession> sessions = subscriptions.computeIfAbsent(
                subscriptionKey,
                ignored -> new CopyOnWriteArrayList<>()
        );
        StreamSession session;
        synchronized (sessions) {
            if (sessions.size() >= properties.maxConnectionsPerUserRun()) {
                throw new ApiException(AiRunErrorCode.AI_RUN_SSE_CONNECTION_LIMIT);
            }
            SseEmitter emitter = new SseEmitter(properties.timeout().toMillis());
            session = new StreamSession(emitter, lastEventId == null ? -1L : lastEventId);
            sessions.add(session);
        }
        registerCallbacks(subscriptionKey, session);

        List<AiRunPublicEvent> replay = histories.getOrDefault(runKey, EventHistory.EMPTY)
                .after(lastEventId == null ? -1L : lastEventId);
        if (replay.isEmpty() && (lastEventId == null || current.version() > lastEventId)) {
            replay = List.of(AiRunPublicEvent.from(current));
        }
        replay.forEach(event -> send(session, subscriptionKey, event));

        AiRunPublicEvent currentEvent = AiRunPublicEvent.from(current);
        if (currentEvent.terminal() && currentEvent.eventId() <= session.lastSentEventId()) {
            complete(subscriptionKey, session);
        }
        return session.emitter();
    }

    @Scheduled(fixedDelayString = "${app.ai-run.sse.heartbeat-interval:15s}")
    void heartbeatAndCleanup() {
        subscriptions.forEach((key, sessions) -> sessions.forEach(session -> {
            try {
                session.emitter().send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException exception) {
                complete(key, session);
            }
        }));

        Instant cutoff = clock.instant().minus(properties.historyRetention());
        histories.entrySet().removeIf(entry -> entry.getValue().lastPublishedAt().isBefore(cutoff));
    }

    private Long parseLastEventId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.strip());
            if (parsed < 0) {
                throw new NumberFormatException("negative event id");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new ApiException(AiRunErrorCode.AI_RUN_INVALID_LAST_EVENT_ID);
        }
    }

    private void send(StreamSession session, SubscriptionKey key, AiRunPublicEvent event) {
        try {
            boolean sent = session.send(event);
            if (sent && event.terminal()) {
                complete(key, session);
            }
        } catch (IOException | IllegalStateException exception) {
            complete(key, session);
        }
    }

    private void registerCallbacks(SubscriptionKey key, StreamSession session) {
        session.emitter().onCompletion(() -> remove(key, session));
        session.emitter().onTimeout(() -> complete(key, session));
        session.emitter().onError(ignored -> remove(key, session));
    }

    private void complete(SubscriptionKey key, StreamSession session) {
        remove(key, session);
        try {
            session.emitter().complete();
        } catch (IllegalStateException ignored) {
            // 이미 완료된 연결입니다.
        }
    }

    private void remove(SubscriptionKey key, StreamSession session) {
        CopyOnWriteArrayList<StreamSession> sessions = subscriptions.get(key);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            subscriptions.remove(key, sessions);
        }
    }

    private record RunKey(UUID companyId, UUID aiRunId) {
    }

    private record SubscriptionKey(UUID companyId, UUID actorId, UUID aiRunId) {
    }

    private static final class StreamSession {
        private final SseEmitter emitter;
        private long lastSentEventId;

        private StreamSession(SseEmitter emitter, long lastSentEventId) {
            this.emitter = emitter;
            this.lastSentEventId = lastSentEventId;
        }

        private synchronized boolean send(AiRunPublicEvent event) throws IOException {
            if (event.eventId() <= lastSentEventId) {
                return false;
            }
            emitter.send(SseEmitter.event()
                    .id(Long.toString(event.eventId()))
                    .name(event.type().name())
                    .data(event));
            lastSentEventId = event.eventId();
            return true;
        }

        private SseEmitter emitter() {
            return emitter;
        }

        private synchronized long lastSentEventId() {
            return lastSentEventId;
        }
    }

    private static final class EventHistory {
        private static final EventHistory EMPTY = new EventHistory();

        private final List<AiRunPublicEvent> events = new ArrayList<>();
        private Instant lastPublishedAt = Instant.EPOCH;

        private synchronized void record(AiRunPublicEvent event, Instant publishedAt, int limit) {
            if (events.stream().noneMatch(existing -> existing.eventId() == event.eventId())) {
                events.add(event);
                events.sort(Comparator.comparingLong(AiRunPublicEvent::eventId));
                while (events.size() > limit) {
                    events.remove(0);
                }
            }
            lastPublishedAt = publishedAt;
        }

        private synchronized List<AiRunPublicEvent> after(long eventId) {
            return events.stream()
                    .filter(event -> event.eventId() > eventId)
                    .toList();
        }

        private synchronized Instant lastPublishedAt() {
            return lastPublishedAt;
        }
    }
}
