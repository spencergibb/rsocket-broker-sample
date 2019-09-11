package org.springframework.cloud.rsocket.sample.pong;

import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.rsocket.client.BrokerClient;
import org.springframework.cloud.gateway.rsocket.client.GatewayRSocketClientAutoConfiguration;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@SpringBootApplication
@Import(GatewayRSocketClientAutoConfiguration.class)
public class PongApplication {

	public static void main(String[] args) {
		SpringApplication.run(PongApplication.class, args);
	}

	@Bean
	public Pong pong(BrokerClient brokerClient) {
		return new Pong(brokerClient);
	}

	static String reply(String in) {
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

	@Slf4j
	public static class Pong implements ApplicationListener<ApplicationReadyEvent> {

		private final BrokerClient brokerClient;

		public Pong(BrokerClient brokerClient) {
			this.brokerClient = brokerClient;
		}

		@Override
		@SuppressWarnings("Duplicates")
		public void onApplicationEvent(ApplicationReadyEvent event) {
			ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();

			Boolean isClient = env.getProperty("pong.client", Boolean.class, true);

			log.info("Starting Pong isClient: " + isClient);

			if (isClient) {
				brokerClient.connect().block();
				/*RSocketFactory.connect()
						.metadataMimeType(Metadata.ROUTING_MIME_TYPE)
						.setupPayload(DefaultPayload
								.create(EMPTY_BUFFER, announcementMetadata))
						.addClientPlugin(interceptor)
						.acceptor(this)
						.transport(TcpClientTransport.create(port)) // proxy
						.start()
						.block();*/
			} else { // start server
				/*RSocketFactory.receive()
						.addServerPlugin(interceptor)
						.acceptor(this)
						.transport(TcpServerTransport.create(port)) // listen on port
						.start()
						.subscribe();*/
			}
		}
	}

	@Slf4j
	@Controller
	public static class PongController {
		private final AtomicInteger pingsReceived = new AtomicInteger();

		public PongController() {
			System.out.println("here");
		}

		@MessageMapping("pong-rr")
		public Mono<String> pong(String ping) {
			logPings(ping);
			return Mono.just(reply(ping));
		}

		@MessageMapping("pong-rc")
		public Flux<String> pong(Flux<String> pings) {
			return pings.doOnNext(this::logPings)
					.map(PongApplication::reply);
		}

		private void logPings(String str) {
			int received = pingsReceived.incrementAndGet();
			log.info("received " + str + "("+received+") in Pong");
		}

	}

}

