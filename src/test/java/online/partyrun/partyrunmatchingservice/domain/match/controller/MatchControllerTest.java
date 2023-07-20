package online.partyrun.partyrunmatchingservice.domain.match.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.restdocs.webtestclient.WebTestClientRestDocumentation.document;

import lombok.extern.slf4j.Slf4j;
import online.partyrun.partyrunmatchingservice.config.docs.WebfluxDocsTest;
import online.partyrun.partyrunmatchingservice.domain.match.domain.Match;
import online.partyrun.partyrunmatchingservice.domain.match.domain.MatchMember;
import online.partyrun.partyrunmatchingservice.domain.match.dto.MatchEvent;
import online.partyrun.partyrunmatchingservice.domain.match.dto.MatchRequest;
import online.partyrun.partyrunmatchingservice.domain.match.service.MatchService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.Flow;

@Slf4j
@ContextConfiguration(classes = MatchController.class)
@DisplayName("MatchController")
@WithMockUser
class MatchControllerTest extends WebfluxDocsTest {

    @MockBean MatchService matchService;
    final Match match = new Match(List.of(new MatchMember("현준"), new MatchMember("준혁")), 1000);

    @Test
    @DisplayName("post : match 수락 여부 전송")
    void postMatching() {

        given(matchService.setMemberStatus(any(), any())).willReturn(Mono.just(match));

        client.post()
                .uri("/match")
                .bodyValue(new MatchRequest(true))
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody()
                .consumeWith(document("create-match"));
    }

    @Test
    @DisplayName("get : waiting event 요청")
    void getMatching() {
        given(matchService.subscribe(any()))
                .willReturn(Flux.just(new MatchEvent(match), new MatchEvent(match)));
        client.get()
                .uri("/match/event")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .consumeWith(document("get-match-event"));
    }

    @Test
    @DisplayName("test")
    void test() {
        final Flux<String> just = Flux.just("a", "b");

        final Disposable subscribe = just.map(data -> data + "hello")
                .doOnNext(data -> log.info("do next {}", data))
                .subscribe(m -> log.info("do sub {}", m));
    }
}
