package online.partyrun.partyrunmatchingservice.domain.waiting.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.experimental.FieldDefaults;

import online.partyrun.partyrunmatchingservice.domain.match.dto.MatchRequest;
import online.partyrun.partyrunmatchingservice.domain.match.service.MatchService;
import online.partyrun.partyrunmatchingservice.domain.waiting.domain.RunningDistance;
import online.partyrun.partyrunmatchingservice.domain.waiting.domain.WaitingEvent;
import online.partyrun.partyrunmatchingservice.domain.waiting.domain.WaitingUser;
import online.partyrun.partyrunmatchingservice.domain.waiting.dto.CreateWaitingRequest;
import online.partyrun.partyrunmatchingservice.domain.waiting.dto.WaitingEventResponse;
import online.partyrun.partyrunmatchingservice.domain.waiting.message.WaitingPublisher;
import online.partyrun.partyrunmatchingservice.global.dto.MessageResponse;
import online.partyrun.partyrunmatchingservice.global.handler.ServerSentEventHandler;

import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Arrays;
import java.util.List;

@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
public class WaitingService implements MessageListener {
    WaitingPublisher waitingPublisher;
    ServerSentEventHandler<String, WaitingEvent> waitingEventHandler;
    SubscribeBuffer buffer;
    MatchService matchService;
    RedisSerializer<WaitingUser> serializer;

    private static final int SATISFY_COUNT = 2;

    public Mono<MessageResponse> register(Mono<String> runner, CreateWaitingRequest request) {
        return runner.map(
                id -> {
                    waitingEventHandler.addSink(id);
                    waitingPublisher.publish(new WaitingUser(id, request.distance()));
                    return new MessageResponse(id + "님 대기열 등록");
                });
    }

    /**
     * 대기열을 구독합니다. sink에 연결한 후, 들어오는 event를 사용자에게 바로 전달합니다.
     *
     * @param member 사용자 id가 담겨있는 {@link Mono}
     * @return event stream
     * @author Hyeonjun Park
     * @since 2023-06-29
     */
    public Flux<WaitingEventResponse> subscribe(Mono<String> member) {
        return member.flatMapMany(
                id ->
                        waitingEventHandler
                                .connect(id)
                                .subscribeOn(Schedulers.boundedElastic())
                                .doOnNext( // 데이터를 하나 받을 때마다 실행
                                        event -> {
                                            if (!event.equals(WaitingEvent.CONNECT)) {
                                                waitingEventHandler.complete(id);
                                            }
                                        })
                                .map(WaitingEventResponse::new));
    }

    @Override
    public void onMessage(final Message message, final byte[] pattern) {
        buffer.add(serializer.deserialize(message.getBody()));
        processMessages();
    }

    @Synchronized
    private void processMessages() {
        Arrays.stream(RunningDistance.values())
                .forEach(
                        distance -> {
                            if (buffer.satisfyCount(distance, SATISFY_COUNT)) {
                                List<String> memberIds = buffer.flush(distance, SATISFY_COUNT);
                                // 매칭 생성 보내기
                                matchService.create(memberIds, distance).subscribe();
                                // Event 추가하기
                                memberIds.forEach(
                                        m ->
                                                waitingEventHandler.sendEvent(
                                                        m, WaitingEvent.MATCHED));
                            }
                        });
    }

    @Scheduled(fixedDelay = 14_400_000) // 12시간 마다 실행
    public void removeUnConnectedSink() {
        waitingEventHandler.getConnectors().stream()
                .filter(connect -> !buffer.hasElement(connect))
                .forEach(
                        member -> {
                            matchService
                                    .setMemberStatus(Mono.just(member), new MatchRequest(false))
                                    .subscribe();
                            waitingEventHandler.complete(member);
                        });
    }
}
