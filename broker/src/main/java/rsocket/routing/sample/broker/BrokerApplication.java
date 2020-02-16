package rsocket.routing.sample.broker;

import reactor.core.publisher.Hooks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BrokerApplication {

	public static void main(String[] args) {
		Hooks.onOperatorDebug();
		//ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
		SpringApplication.run(BrokerApplication.class, args);
	}

}

