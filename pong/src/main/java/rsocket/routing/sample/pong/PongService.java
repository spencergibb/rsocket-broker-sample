package rsocket.routing.sample.pong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.stereotype.Service;

@Service
public class PongService {

	private static final Logger log = LoggerFactory.getLogger(PongService.class);

	private final Environment env;

	public PongService(Environment env) {
		this.env = env;
	}

	@EventListener
	public void onRSocketRequester(RSocketRequester requester) {
		Boolean isClient = env.getProperty("pong.client", Boolean.class, true);

		log.info("Starting Pong isClient: " + isClient);

		if (!isClient) {
			/*FIXME: RSocketFactory.receive()
					.addServerPlugin(interceptor)
					.acceptor(this)
					.transport(TcpServerTransport.create(port)) // listen on port
					.start()
					.subscribe();*/
		}
	}

}

