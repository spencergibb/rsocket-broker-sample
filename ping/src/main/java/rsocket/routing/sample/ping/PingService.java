package rsocket.routing.sample.ping;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.rsocket.routing.client.spring.SpringRoutingClient;
import io.rsocket.routing.config.RoutingClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Service;

@Service
public class PingService implements ApplicationListener<PayloadApplicationEvent<RSocketRequester>> {

	private static final Logger logger = LoggerFactory.getLogger(PingService.class);

	private final SpringRoutingClient client;

	private final PingProperties properties;
	private final RoutingClientProperties routingClientProperties;

	private final AtomicInteger pongsReceived = new AtomicInteger();

	public PingService(SpringRoutingClient client, PingProperties properties,
			RoutingClientProperties routingClientProperties) {
		this.client = client;
		this.properties = properties;
		this.routingClientProperties = routingClientProperties;
	}

	@Override
	public void onApplicationEvent(PayloadApplicationEvent<RSocketRequester> event) {
		logger.info("Starting Ping" + routingClientProperties.getRouteId() + " request type: " + properties.getRequestType());
		//RSocketRequester requester = client.connect().retry(5).block();
		RSocketRequester requester = event.getPayload();

		switch (properties.getRequestType()) {
			case REQUEST_RESPONSE:
				Flux.interval(Duration.ofSeconds(1))
						.flatMap(i -> requester.route("pong-rr")
								.metadata(client.address("pong"))
								.data("ping" + i)
								.retrieveMono(String.class)
								.doOnNext(this::logPongs))
						//.then().block();
						.subscribe();
				break;

			case REQUEST_CHANNEL:
				requester.route("pong-rc")
						// metadata not needed. Auto added with gateway rsocket client via properties
						//.metadata(client.forwarding(builder -> builder.serviceName("pong").with("multicast", "true")))
						.data(Flux.interval(Duration.ofSeconds(1)).map(this::getPayload)
								.onBackpressureDrop(payload -> logger
										.info("Backpressure applied, dropping payload " + payload)))
						.retrieveFlux(String.class)
						.doOnNext(this::logPongs)
						//.then().block();
						.subscribe();
				break;

			case ACTUATOR:
				throw new UnsupportedOperationException("ACTUATOR not implemented");
				//requester.route("hello")
				//		.metadata(client.address(addr -> addr.with(WellKnownKey.SERVICE_NAME, "gateway")
				//				.disableProxy()))
				//		.data("ping")
				//		.retrieveMono(String.class)
				//		.doOnNext(s -> logger.info("received from actuator: " + s))
				//		.then().block();
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
}
