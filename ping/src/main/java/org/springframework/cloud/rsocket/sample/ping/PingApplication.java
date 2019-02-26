package org.springframework.cloud.rsocket.sample.ping;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.micrometer.MicrometerRSocketInterceptor;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.rsocket.support.Metadata;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;

@SpringBootApplication
public class PingApplication {

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
	}

	@Component
	@Slf4j
	public static class Ping implements ApplicationListener<ApplicationReadyEvent> {

		@Autowired
		private MeterRegistry meterRegistry;

		private final String id;

		private final AtomicInteger pongsReceived = new AtomicInteger();
		private Flux<String> pongFlux;

		public Ping(Environment env) {
			this.id = env.getProperty("ping.id", "1");
		}

		@Override
		public void onApplicationEvent(ApplicationReadyEvent event) {
			log.info("Starting Ping"+id);
			ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();
			Integer gatewayPort = env.getProperty("spring.cloud.gateway.rsocket.server.port",
					Integer.class, 7002);


			MicrometerRSocketInterceptor interceptor = new MicrometerRSocketInterceptor(meterRegistry, Tag
					.of("component", "ping"));
			ByteBuf announcementMetadata = Metadata.from("ping").with("id", "ping"+id).encode();
			pongFlux = RSocketFactory.connect()
					.metadataMimeType(Metadata.ROUTING_MIME_TYPE)
					.setupPayload(DefaultPayload.create(EMPTY_BUFFER, announcementMetadata))
					.addClientPlugin(interceptor)
					.transport(TcpClientTransport.create(gatewayPort)) // proxy
					.start()
					.flatMapMany(this::handleRSocket);

			pongFlux.subscribe();
		}

		private Flux<String> handleRSocket(RSocket socket) {
			return socket.requestChannel(sendPings()
					// this is needed in case pong is not available yet
					.onBackpressureDrop(payload -> log.info("Dropped payload " + payload.getDataUtf8()))
			).map(Payload::getDataUtf8)
					.doOnNext(this::logPongs);
		}

		private Flux<Payload> sendPings() {
			return Flux.interval(Duration.ofSeconds(1))
					.map(i -> {
						ByteBuf data = ByteBufUtil
								.writeUtf8(ByteBufAllocator.DEFAULT, "ping" + id);
						ByteBuf routingMetadata = Metadata.from("pong").encode();
						return DefaultPayload.create(data, routingMetadata);
					});
		}

		private void logPongs(String payload) {
			int received = pongsReceived.incrementAndGet();
			log.info("received " + payload + "(" + received + ") in Ping" + id);
		}
	}
}

