package org.springframework.cloud.rsocket.sample.ping;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ping")
public class PingProperties {

	private RequestType requestType = RequestType.REQUEST_CHANNEL;

	public RequestType getRequestType() {
		return requestType;
	}

	public void setRequestType(RequestType requestType) {
		this.requestType = requestType;
	}

	static enum RequestType {
		REQUEST_CHANNEL,
		REQUEST_RESPONSE,
		ACTUATOR
	}
}
