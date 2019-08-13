package org.springframework.cloud.rsocket.sample.pong;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.ConnectionSetupPayload;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.SocketAcceptor;
import io.rsocket.micrometer.MicrometerRSocketInterceptor;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import io.rsocket.util.RSocketProxy;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.stereotype.Controller;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;
import static org.springframework.cloud.gateway.rsocket.support.WellKnownKey.INSTANCE_NAME;

@SpringBootApplication
public class PongApplication {

	public static void main(String[] args) {
		SpringApplication.run(PongApplication.class, args);
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
	public Pong pong(Environment env, MeterRegistry meterRegistry,
			RSocketRequester.Builder requesterBuilder, RSocketStrategies strategies,
			RSocketMessageHandler handler) {
		//TODO: client module
		GatewayRSocketAutoConfiguration.registerMimeTypes(strategies);
		requesterBuilder.rsocketFactory(rsocketFactory -> rsocketFactory.acceptor(handler.responder()));
		return new Pong(env, meterRegistry, requesterBuilder);
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
	public static class Pong implements ApplicationListener<ApplicationReadyEvent>,
			SocketAcceptor, Function<RSocket, RSocket> {

		private final String id;

		private final MeterRegistry meterRegistry;

		private final RSocketRequester.Builder requesterBuilder;

		private final AtomicInteger pingsReceived = new AtomicInteger();

		public Pong(Environment env, MeterRegistry meterRegistry, RSocketRequester.Builder requesterBuilder) {
			this.id = env.getProperty("pong.id", "3");
			this.meterRegistry = meterRegistry;
			this.requesterBuilder = requesterBuilder;
		}

		@Override
		@SuppressWarnings("Duplicates")
		public void onApplicationEvent(ApplicationReadyEvent event) {
			ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();

			Boolean isClient = env.getProperty("pong.client", Boolean.class, true);

			log.info("Starting Pong isClient: " + isClient);
			Integer port = env.getProperty("spring.cloud.gateway.rsocket.server.port",
					Integer.class, 7002);
			MicrometerRSocketInterceptor interceptor = new MicrometerRSocketInterceptor(meterRegistry, Tag
					.of("component", "pong"));
			//ByteBuf announcementMetadata = Metadata.from("pong").with("id", "pong" + id).encode();

			if (isClient) {
				TagsMetadata tagsMetadata = TagsMetadata.builder()
						.with(INSTANCE_NAME, "pong" + id)
						.build();
				RouteSetup routeSetup = new RouteSetup(new Long(id), "pong", tagsMetadata.getTags());
				requesterBuilder.setupMetadata(routeSetup, RouteSetup.ROUTE_SETUP_MIME_TYPE)
						.connectTcp("localhost", port)
						.block();
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
				RSocketFactory.receive()
						.addServerPlugin(interceptor)
						.acceptor(this)
						.transport(TcpServerTransport.create(port)) // listen on port
						.start()
						.subscribe();
			}
		}

		@Override
		public Mono<RSocket> accept(ConnectionSetupPayload setup, RSocket sendingSocket) {
			return Mono.just(apply(sendingSocket));
		}

		@Override
		public RSocket apply(RSocket rSocket) {
			return new RSocketProxy(rSocket) {

				@Override
				public Mono<Payload> requestResponse(Payload payload) {
					return Mono.just(payload)
							.map(Payload::getDataUtf8)
							.doOnNext(this::logPings)
							.map(PongApplication::reply)
							.map(this::toPayload);
				}

				@Override
				public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
					return Flux.from(payloads)
							.map(Payload::getDataUtf8)
							.doOnNext(this::logPings)
							.map(PongApplication::reply)
							.map(this::toPayload);
				}

				private void logPings(String str) {
					int received = pingsReceived.incrementAndGet();
					log.info("received " + str + "("+received+") in Pong");
				}

				private Payload toPayload(String reply) {
					ByteBuf data = ByteBufUtil.writeUtf8(ByteBufAllocator.DEFAULT, reply);
					return DefaultPayload.create(data, EMPTY_BUFFER);
				}
			};
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

