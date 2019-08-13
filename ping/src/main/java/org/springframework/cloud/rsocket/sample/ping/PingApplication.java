package org.springframework.cloud.rsocket.sample.ping;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.rsocket.micrometer.MicrometerRSocketInterceptor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.rsocket.messaging.RSocketStrategiesCustomizer;
import org.springframework.cloud.gateway.rsocket.autoconfigure.GatewayRSocketAutoConfiguration;
import org.springframework.cloud.gateway.rsocket.support.Forwarding;
import org.springframework.cloud.gateway.rsocket.support.RouteSetup;
import org.springframework.cloud.gateway.rsocket.support.TagsMetadata;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;

import static org.springframework.cloud.gateway.rsocket.support.WellKnownKey.INSTANCE_NAME;
import static org.springframework.cloud.gateway.rsocket.support.WellKnownKey.SERVICE_NAME;

@SpringBootApplication
public class PingApplication {

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
	}

	@Bean
	//TODO: client module?
	public RSocketStrategiesCustomizer gatewayRSocketStrategiesCustomizer() {
		return strategies -> {
			strategies.decoder(new Forwarding.Decoder(), new RouteSetup.Decoder())
					.encoder(new Forwarding.Encoder(), new RouteSetup.Encoder());
		};
	}

	@Bean
	public Ping ping(Environment env, MeterRegistry meterRegistry, RSocketRequester.Builder requesterBuilder, RSocketStrategies strategies) {
		//TODO: client module
		GatewayRSocketAutoConfiguration.registerMimeTypes(strategies);
		return new Ping(env, meterRegistry, requesterBuilder);
	}

	@Slf4j
	public static class Ping implements ApplicationListener<ApplicationReadyEvent> {

		private MeterRegistry meterRegistry;
		private final RSocketRequester.Builder requesterBuilder;

		private final String id;

		private final AtomicInteger pongsReceived = new AtomicInteger();
		private Flux<String> pongFlux;

		public Ping(Environment env, MeterRegistry meterRegistry, RSocketRequester.Builder requesterBuilder) {
			this.id = env.getProperty("ping.id", "1");
			this.meterRegistry = meterRegistry;
			this.requesterBuilder = requesterBuilder;
		}

		@Override
		public void onApplicationEvent(ApplicationReadyEvent event) {
			ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();

			String requestType = env.getProperty("ping.request-type", "request-channel");
			log.info("Starting Ping"+id+" request type: " + requestType);

			Integer serverPort = env.getProperty("spring.rsocket.server.port",
					Integer.class, 7002);

			MicrometerRSocketInterceptor interceptor = new MicrometerRSocketInterceptor(meterRegistry, Tag
					.of("component", "ping"));

			TagsMetadata tagsMetadata = TagsMetadata.builder()
					.with(INSTANCE_NAME, "ping" + id)
					.build();
			RouteSetup routeSetup = new RouteSetup(new Long(id), "ping", tagsMetadata.getTags());
			RSocketRequester requester = requesterBuilder
					.setupMetadata(routeSetup, RouteSetup.ROUTE_SETUP_MIME_TYPE)
					.connectTcp("localhost", serverPort)
					.block();

			TagsMetadata forwardinMetadata = TagsMetadata.builder()
					.with(SERVICE_NAME, "pong")
					.build();
			Forwarding forwarding = new Forwarding(new Long(id), forwardinMetadata.getTags());

			if (requestType.equals("request-response")) {
				Flux.interval(Duration.ofSeconds(1))
					.flatMap(i -> requester.route("pong-rr")
							.metadata(forwarding, Forwarding.FORWARDING_MIME_TYPE)
							.data("ping" + i)
							.retrieveMono(String.class)
							.doOnNext(this::logPongs))
					.subscribe();

			} else {
				requester.route("pong-rc")
						.metadata(forwarding, Forwarding.FORWARDING_MIME_TYPE)
						.data(Flux.interval(Duration.ofSeconds(1)).map(this::getPayload)
								.onBackpressureDrop(payload -> log
										.info("Backpressure applied, dropping payload " + payload)))
						.retrieveFlux(String.class)
						.subscribe(this::logPongs);
			}
		}

		private String getPayload(long i) {
			return "ping" + i;
		}

		private void logPongs(String payload) {
			int received = pongsReceived.incrementAndGet();
			log.info("received " + payload + "(" + received + ") in Ping" + id);
		}
	}
}

