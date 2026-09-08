package com.axonivy.connector.onlyoffice;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.MessageFormatMessageFactory;

/**
 * Currently, CSRF can only be switched on or off for the whole engine. This Provider implements
 * exceptions for state-changing (not GET) REST calls, so that they do not require the X-Requested-By header.
 */
@Provider
@PreMatching
public class OnlyOfficeProvider implements ContainerRequestFilter {
	private static final Logger LOG = LogManager.getLogger(new MessageFormatMessageFactory());

	@Context
	private HttpServletRequest servletRequest;

	@Override
	public void filter(ContainerRequestContext rqCtx) throws IOException {
		// If this path is an exception, add the CSRF header here automatically.
		if(OnlyOfficeResource.isCsrfException(servletRequest.getPathInfo())) {
			rqCtx.getHeaders().add("X-Requested-By", servletRequest.getRemoteAddr());
		}
	}
}
