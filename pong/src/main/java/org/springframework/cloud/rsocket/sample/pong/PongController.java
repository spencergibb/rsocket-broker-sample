package org.springframework.cloud.rsocket.sample.pong;

import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
public class PongController {
	private final AtomicInteger pingsReceived = new AtomicInteger();

	@MessageMapping("pong-rr")
	public Mono<String> pong(String ping) {
		logPings(ping);
		return Mono.just(reply(ping));
	}

	@MessageMapping("pong-rc")
	public Flux<String> pong(Flux<String> pings) {
		return pings.doOnNext(this::logPings)
				.map(this::reply);
	}

	private void logPings(String str) {
		int received = pingsReceived.incrementAndGet();
		log.info("received " + str + "("+received+") in Pong");
	}

	String reply(String in) {
		if (in.length() > 4) {
			in = in.substring(0, 4);
		}
		switch (in.toLowerCase()) {
		case "ping":
			return "pong";
		default:
			throw new IllegalArgumentException("Value must be ping, not " + in);
		}
	}

}
