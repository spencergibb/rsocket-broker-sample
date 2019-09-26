package org.springframework.cloud.rsocket.sample.pong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.cloud.gateway.rsocket.client.BrokerClient;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.core.env.Environment;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Service;

@Service
public class PongService implements ApplicationListener<PayloadApplicationEvent<RSocketRequester>> {

	private static final Logger log = LoggerFactory.getLogger(PongService.class);

	private final BrokerClient brokerClient;
	private final Environment env;

	public PongService(BrokerClient brokerClient, Environment env) {
		this.brokerClient = brokerClient;
		this.env = env;
	}

	@Override
	@SuppressWarnings("Duplicates")
	public void onApplicationEvent(PayloadApplicationEvent<RSocketRequester> event) {
		Boolean isClient = env.getProperty("pong.client", Boolean.class, true);

		log.info("Starting Pong isClient: " + isClient);

		if (isClient) {
			//brokerClient.connect().block();
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

