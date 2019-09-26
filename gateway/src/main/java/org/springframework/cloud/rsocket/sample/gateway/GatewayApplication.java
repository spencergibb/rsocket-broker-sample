package org.springframework.cloud.rsocket.sample.gateway;

import io.netty.util.ResourceLeakDetector;
import reactor.core.publisher.Hooks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GatewayApplication {

	public static void main(String[] args) {
		Hooks.onOperatorDebug();
		//ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID);
		SpringApplication.run(GatewayApplication.class, args);
	}

}

