package org.springframework.cloud.rsocket.sample.gateway;

import java.util.HashMap;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.netty.buffer.ByteBuf;
import io.rsocket.RSocket;
import io.rsocket.RSocketFactory;
import io.rsocket.micrometer.MicrometerRSocketInterceptor;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.util.DefaultPayload;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.rsocket.registry.Registry;
import org.springframework.cloud.gateway.rsocket.server.GatewayRSocket;
import org.springframework.cloud.gateway.rsocket.support.Metadata;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Component;

import static io.netty.buffer.Unpooled.EMPTY_BUFFER;

@SpringBootApplication
public class GatewayApplication {

	public static void main(String[] args) {
		SpringApplication.run(GatewayApplication.class, args);
	}

	@Component
	@Slf4j
	@Profile("pongproxy")
	public static class PongProxy implements ApplicationListener<ApplicationReadyEvent> {

		@Autowired
		private Registry registry;

		@Autowired
		private MeterRegistry meterRegistry;

		@Autowired
		private GatewayRSocket.Factory rsocketFactory;

		@Override
		@SuppressWarnings("Duplicates")
		public void onApplicationEvent(ApplicationReadyEvent event) {
			ConfigurableEnvironment env = event.getApplicationContext().getEnvironment();
			log.info("Starting Pong Proxy");
			MicrometerRSocketInterceptor interceptor = new MicrometerRSocketInterceptor(meterRegistry, Tag
					.of("component", "pongproxy"));
			ByteBuf announcementMetadata = Metadata.encodeTags("name:pong", "id:pongproxy1");
			RSocketFactory.connect()
					.metadataMimeType(Metadata.ROUTING_MIME_TYPE)
					.setupPayload(DefaultPayload.create(EMPTY_BUFFER, announcementMetadata))
					.addClientPlugin(interceptor)
					.acceptor(this::accept)
					.transport(TcpClientTransport.create(7002)) // gateway1
					.start()
					.subscribe();
		}

		@SuppressWarnings("Duplicates")
		RSocket accept(RSocket rSocket) {
			HashMap<String, String> metadata = new HashMap<>();
			metadata.put("name", "ping");
			metadata.put("id", "pingproxy1");
			registry.register(metadata, rSocket);
			return rsocketFactory.create(metadata);
		}
	}
}

