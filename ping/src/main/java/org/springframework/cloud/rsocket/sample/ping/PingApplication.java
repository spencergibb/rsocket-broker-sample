package org.springframework.cloud.rsocket.sample.ping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(PingProperties.class)
public class PingApplication {

	public static void main(String[] args) {
		SpringApplication.run(PingApplication.class, args);
	}

}

