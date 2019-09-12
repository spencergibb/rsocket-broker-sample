package org.springframework.cloud.rsocket.sample.pong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cloud.gateway.rsocket.client.BrokerClient;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.stereotype.Service;

@Service
public class PongService implements ApplicationListener<ApplicationReadyEvent> {

	private static final Logger log = LoggerFactory.getLogger(PongService.class);

	private final BrokerClient brokerClient;

	public PongService(BrokerClient brokerClient) {
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

