package com.ote.mandate.business.command.domain;

import com.ote.common.cqrs.IEvent;
import com.ote.mandate.business.aggregate.Mandate;
import com.ote.mandate.business.aggregate.MandateProjector;
import com.ote.mandate.business.command.api.IRemoveHeirsCommandHandler;
import com.ote.mandate.business.command.exception.MandateNotYetCreatedException;
import com.ote.mandate.business.command.model.RemoveHeirsCommand;
import com.ote.mandate.business.command.spi.IEventRepository;
import com.ote.mandate.business.event.model.MandateHeirRemovedEvent;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class RemoveHeirsCommandHandler implements IRemoveHeirsCommandHandler {

    private final IEventRepository eventRepository;

    @Override
    public Mono<Boolean> removeHeirs(Mono<RemoveHeirsCommand> command) {

        return command.
                doOnNext(cmd -> log.debug("Trying to remove heirs : {}", cmd)).
                flatMap(cmd -> getOrRaiseError(cmd, events -> CollectionUtils.isNotEmpty(events), () -> new MandateNotYetCreatedException(cmd.getId()), eventRepository)).
                map(this::createEvents).
                flatMapMany(events -> Flux.fromStream(events.stream())).
                flatMap(event -> eventRepository.storeAndPublish(Mono.just(event))).
                collectList().
                map(list -> list.stream().anyMatch(p -> p == true));
    }

    private List<IEvent> createEvents(Tuple2<RemoveHeirsCommand, List<IEvent>> tuple) {
        return createEvents(tuple.getT1(), tuple.getT2());
    }

    private List<IEvent> createEvents(RemoveHeirsCommand command, List<IEvent> events) {

        try (MandateProjector mandateProjector = new MandateProjector()) {
            Mandate mandate = mandateProjector.apply(events);
            return command.getOtherHeirs().stream().
                    filter(heir -> CollectionUtils.containsAny(mandate.getOtherHeirs(), heir)).
                    map(heir -> new MandateHeirRemovedEvent(command.getId(), heir)).
                    collect(Collectors.toList());
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
}
