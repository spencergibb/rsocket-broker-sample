package org.springframework.cloud.rsocket.sample.ping;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.rsocket.client.BrokerClient;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.messaging.rsocket.RSocketRequester;

@SpringBootApplication
public class PingApplication {

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
	}

	@Bean
	public Ping ping(BrokerClient brokerClient) {
		return new Ping(brokerClient);
	}

	@Slf4j
	public static class Ping implements ApplicationListener<ApplicationReadyEvent> {

		private final BrokerClient client;

		private final AtomicInteger pongsReceived = new AtomicInteger();

		public Ping(BrokerClient client) {
			this.client = client;
		}

		@Override
		public void onApplicationEvent(ApplicationReadyEvent event) {
			ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();

			String requestType = env.getProperty("ping.request-type", "request-channel");
			log.info("Starting Ping" + client.getProperties().getRouteId() + " request type: " + requestType);

			RSocketRequester requester = client.connect().block();

			if (requestType.equals("actuator")) {
				requester.route("hello")
						.metadata(client.forwarding(fwd -> fwd.serviceName("gateway")
								.disableProxy()))
						.data("ping")
						.retrieveMono(String.class)
						.subscribe(s -> log.info("received from actuator: " + s));
			}
			else if (requestType.equals("request-response")) {
				Flux.interval(Duration.ofSeconds(1))
						.flatMap(i -> requester.route("pong-rr")
								.metadata(client.forwarding("pong"))
								.data("ping" + i)
								.retrieveMono(String.class)
								.doOnNext(this::logPongs))
						.subscribe();

			}
			else {
				requester.route("pong-rc")
						.metadata(client.forwarding("pong"))
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
			log.info("received " + payload + "(" + received + ") in Ping" + client.getProperties().getRouteId());
		}
	}
}

