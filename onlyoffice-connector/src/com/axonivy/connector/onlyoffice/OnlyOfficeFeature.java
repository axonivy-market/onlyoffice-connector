package com.axonivy.connector.onlyoffice;

import java.io.IOException;
import java.net.URI;

import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

/**
 * Replace the configured URL for this call.
 */
public class OnlyOfficeFeature implements Feature {
	public static final String CONFIG_KEY_URL = "replacementUrl";
	@Override
	public boolean configure(FeatureContext context) {
		var urlFilter = new UrlFilter();
		context.register(urlFilter, Priorities.AUTHORIZATION);
		return true;
	}

	public class UrlFilter implements ClientRequestFilter {
		/**
		 * Replace the configured URL by the one stored in a property.
		 */
		@Override
		public void filter(ClientRequestContext context) throws IOException {
			var url = (String)context.getConfiguration().getProperty(CONFIG_KEY_URL);
			context.setUri(URI.create(url));
		}
	}
}
