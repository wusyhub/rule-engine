package org.jetlinks.rule.engine.defaults;

import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.topic.Topic;
import org.jetlinks.rule.engine.api.Decoder;
import org.jetlinks.rule.engine.api.Encoder;
import org.jetlinks.rule.engine.api.EventBus;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

@Slf4j
@SuppressWarnings("all")
public class LocalEventBus implements EventBus {

    private final Topic<FluxSink> subs = Topic.createRoot();

    @Override
    public <T> Flux<T> subscribe(String topic,  Decoder<T> type) {
        return Flux.create((sink) -> {
            log.debug("subscription topic: {}", topic);
            Topic<FluxSink> sub = subs.append(topic);
            sub.subscribe(sink);
            sink.onDispose(() -> {
                log.debug("unsubscription topic: {}", topic);
                sub.unsubscribe(sink);
            });
        });
    }

    @Override
    public <T> Mono<Integer> publish(String topic, Publisher<T> event) {
        return subs
                .findTopic(topic)
                .map(Topic::getSubscribers)
                .flatMap(subscriber -> {
                    log.debug("publish topic: {}",topic);
                    return Flux
                            .from(event)
                            .doOnNext(data -> {
                                for (FluxSink fluxSink : subscriber) {
                                    try {
                                        fluxSink.next(data);
                                    } catch (Exception e) {
                                        log.error(e.getMessage(), e);
                                    }
                                }
                            })
                            .then(Mono.just(subscriber.size()));
                })
                .reduce(Math::addExact)
                ;
    }

    @Override
    public <T> Mono<Integer> publish(String topic, Encoder<T> encoder, Publisher<T> eventStream) {
        return publish(topic,eventStream);
    }
}
