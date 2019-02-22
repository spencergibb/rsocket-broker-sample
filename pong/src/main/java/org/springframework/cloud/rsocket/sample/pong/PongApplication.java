package org.springframework.cloud.rsocket.sample.pong;

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
import io.rsocket.util.RSocketProxy;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
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
public class PongApplication {

	public static void main(String[] args) {
		SpringApplication.run(PongApplication.class, args);
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

	@Component
	@Slf4j
	public static class Pong implements ApplicationListener<ApplicationReadyEvent> {

		private final String id;

		@Autowired
		private MeterRegistry meterRegistry;

		private final AtomicInteger pingsReceived = new AtomicInteger();

		public Pong(Environment env) {
			this.id = env.getProperty("pong.id", "1");
		}

		@Override
		@SuppressWarnings("Duplicates")
		public void onApplicationEvent(ApplicationReadyEvent event) {
			ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();
			log.info("Starting Pong");
			Integer gatewayPort = env.getProperty("spring.cloud.gateway.rsocket.server.port",
					Integer.class, 7002);
			MicrometerRSocketInterceptor interceptor = new MicrometerRSocketInterceptor(meterRegistry, Tag
					.of("component", "pong"));
			ByteBuf announcementMetadata = Metadata.from("pong").with("id", "pong" + id).encode();
			RSocketFactory.connect()
					.metadataMimeType(Metadata.ROUTING_MIME_TYPE)
					.setupPayload(DefaultPayload.create(EMPTY_BUFFER, announcementMetadata))
					.addClientPlugin(interceptor)
					.acceptor(this::accept)
					.transport(TcpClientTransport.create(gatewayPort)) // proxy
					.start()
					.block();
		}

		@SuppressWarnings("Duplicates")
		RSocket accept(RSocket rSocket) {
			RSocket pong = new RSocketProxy(rSocket) {

				@Override
				public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
					return Flux.from(payloads)
							.map(Payload::getDataUtf8)
							.doOnNext(str -> {
								int received = pingsReceived.incrementAndGet();
								log.info("received " + str + "("+received+") in Pong");
							})
							.map(PongApplication::reply)
							.map(reply -> {
								ByteBuf data = ByteBufUtil.writeUtf8(ByteBufAllocator.DEFAULT, reply);
								//ByteBuf routingMetadata = Metadata.from("ping").encode();
								ByteBuf routingMetadata = EMPTY_BUFFER;
								return DefaultPayload.create(data, routingMetadata);
							});
				}
			};
			return pong;
		}

		public int getPingsReceived() {
			return pingsReceived.get();
		}
	}

}

