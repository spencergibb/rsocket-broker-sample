package rsocket.routing.sample.ping;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.rsocket.broker.client.spring.BrokerClientProperties;
import io.rsocket.broker.client.spring.BrokerMetadata;
import io.rsocket.broker.client.spring.BrokerRSocketRequester;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.style.ToStringCreator;
import org.springframework.stereotype.Service;

@Service
public class PingService implements ApplicationListener<ApplicationReadyEvent> {

	private static final Logger logger = LoggerFactory.getLogger(PingService.class);

	private final BrokerRSocketRequester requester;
	private final BrokerMetadata metadata;

	private final PingProperties properties;
	private final BrokerClientProperties routingClientProperties;

	private final AtomicInteger pongsReceived = new AtomicInteger();

	public PingService(BrokerRSocketRequester requester, BrokerMetadata metadata, PingProperties properties,
			BrokerClientProperties routingClientProperties) {
		this.requester = requester;
		this.metadata = metadata;
		this.properties = properties;
		this.routingClientProperties = routingClientProperties;
	}

	@Override
	public void onApplicationEvent(ApplicationReadyEvent event) {
		logger.info("Starting Ping" + routingClientProperties.getRouteId() + " request type: " + properties.getRequestType());

		switch (properties.getRequestType()) {
			case REQUEST_RESPONSE:
				Flux.interval(Duration.ofSeconds(1))
						.flatMap(i -> requester.route("pong-rr")
								.address("pong")
								//.address(builder -> builder.routingType(MULTICAST).with(WellKnownKey.SERVICE_NAME, "pong"))
								//.metadata(metadata.address("pong"))
								.data("ping" + i)
								.retrieveMono(String.class)
								.doOnNext(this::logPongs))
						.subscribe();
				break;

			case REQUEST_CHANNEL:
				requester.route("pong-rc")
						// metadata not needed. Auto added with gateway rsocket client via properties
						//.metadata(metadata.address(addr -> addr.with(SERVICE_NAME, "pong")))
						.data(Flux.interval(Duration.ofSeconds(1)).map(idx -> new PingValue(getPayload(idx)))
								.onBackpressureDrop(payload -> logger
										.info("Backpressure applied, dropping payload " + payload)))
						.retrieveFlux(String.class)
						.doOnNext(this::logPongs)
						.subscribe();
				break;

			case ACTUATOR:
				throw new UnsupportedOperationException("ACTUATOR not implemented");
				//requester.route("hello")
				//		.metadata(client.address(addr -> addr.with(SERVICE_NAME, "gateway")
				//				.disableProxy()))
				//		.data("ping")
				//		.retrieveMono(String.class)
				//		.doOnNext(s -> logger.info("received from actuator: " + s))
				//		.subscribe();
				//break;
		}
	}

	private String getPayload(long i) {
		return "ping" + i;
	}

	private void logPongs(String payload) {
		int received = pongsReceived.incrementAndGet();
		logger.info("received " + payload + "(" + received + ") in Ping" + routingClientProperties.getRouteId());
	}

	class PingValue {
		final String value;

		PingValue(String value) {
			this.value = value;
		}

		public String getValue() {
			return this.value;
		}

		@Override
		public String toString() {
			return new ToStringCreator(this)
					.append("value", value)
					.toString();

		}
	}
}
