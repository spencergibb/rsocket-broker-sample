package org.springframework.cloud.rsocket.sample.ping;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.rsocket.client.BrokerClient;
import org.springframework.context.ApplicationListener;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Service;

@Service
public class PingService implements ApplicationListener<ApplicationReadyEvent> {

	private static final Logger logger = LoggerFactory.getLogger(PingService.class);

	private final BrokerClient client;

	private final PingProperties properties;

	private final AtomicInteger pongsReceived = new AtomicInteger();

	public PingService(BrokerClient client, PingProperties properties) {
		this.client = client;
		this.properties = properties;
	}


	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		logger.info("Starting Ping" + client.getProperties().getRouteId() + " request type: " + properties.getRequestType());
		RSocketRequester requester = client.connect().retry(5).block();

		switch (properties.getRequestType()) {
			case REQUEST_RESPONSE:
				Flux.interval(Duration.ofSeconds(1))
						.flatMap(i -> requester.route("pong-rr")
								.metadata(client.forwarding("pong"))
								.data("ping" + i)
								.retrieveMono(String.class)
								.doOnNext(this::logPongs))
						.then().block();
				break;

			case REQUEST_CHANNEL:
				requester.route("pong-rc")
						.metadata(client.forwarding(builder -> builder.serviceName("pong").with("multicast", "true")))
						.data(Flux.interval(Duration.ofSeconds(1)).map(this::getPayload)
								.onBackpressureDrop(payload -> logger
										.info("Backpressure applied, dropping payload " + payload)))
						.retrieveFlux(String.class)
						.doOnNext(this::logPongs)
						.then().block();
				break;

			case ACTUATOR:
				requester.route("hello")
						.metadata(client.forwarding(fwd -> fwd.serviceName("gateway")
								.disableProxy()))
						.data("ping")
						.retrieveMono(String.class)
						.doOnNext(s -> logger.info("received from actuator: " + s))
						.then().block();
				break;
		}
	}

	private String getPayload(long i) {
		return "ping" + i;
	}

	private void logPongs(String payload) {
		int received = pongsReceived.incrementAndGet();
		logger.info("received " + payload + "(" + received + ") in Ping" + client.getProperties().getRouteId());
	}
}
