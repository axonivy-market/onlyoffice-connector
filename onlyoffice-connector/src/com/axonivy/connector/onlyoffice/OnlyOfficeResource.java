package com.axonivy.connector.onlyoffice;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.regex.Pattern;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.JsonNode;

import ch.ivyteam.ivy.environment.Ivy;

@Path("/documents")
public class OnlyOfficeResource {
	private static final Pattern BEARER_PATTERN = Pattern.compile("Bearer\\s+(\\S+)");
	/**
	 * Paths which will not require the X-Requested-By header.
	 */
	private static final Set<String> CSRF_EXCEPTIONS = Set.of(
			"/documents/callback"
			);

	/**
	 * Paths which will not require the X-Requested-By header.
	 *
	 * @param pathInfo
	 * @return
	 */
	public static boolean isCsrfException(String pathInfo) {
		return CSRF_EXCEPTIONS.contains(pathInfo);
	}

	@GET
	@Path("load/{key}")
	@PermitAll
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response loadDocument(@Context HttpServletRequest rq, @HeaderParam("authorization") String authorization, @PathParam("key") String key) {

		// If secret is set, then check that the key equals the last part of the URL inside the JWT payload.
		JsonNode payload = null;
		if(OnlyOfficeService.get().hasOnlyOfficeJwtSecret()) {
			String payloadKey = null;
			payload = extractJwtPayload(authorization);
			var url = payload != null && payload.hasNonNull("url") ? payload.get("url").asText() : null;
			if(url != null) {
				try {
					var uri = new URI(url);
					if(uri.getPath().endsWith(key)) {
						payloadKey = key;
					}
				} catch (URISyntaxException e) {
					Ivy.log().error("Could not parse url: ''{0}''", e, url);
				}
			}

			key = payloadKey;
		}

		if(key != null) {
			var dei = OnlyOfficeService.get().extractDocumentEditId(key);
			var documentId = dei.documentId();
			Ivy.log().debug("Load call for document: {0}", documentId);

			var doc = OnlyOfficeService.get().getOnlyOfficeDocumentHandler().load(dei.editGroup(), dei.documentId());

			if (doc == null) {
				return Response.status(Response.Status.NOT_FOUND).build();
			}

			return Response.ok(doc.getStream())
					.header("Content-Disposition", "attachment; filename=\"%s\"".formatted(doc.getFileName()))
					.build();
		}
		return Response.status(Status.BAD_REQUEST).build();
	}


	@POST
	@Path("callback")
	@PermitAll
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response callback(@Context HttpServletRequest rq, @HeaderParam("authorization") String authorization, JsonNode payload) {

		// If secret is set, then extract payload from JWT instead of POST body.
		if(OnlyOfficeService.get().hasOnlyOfficeJwtSecret()) {
			payload = extractJwtPayload(authorization);
		}

		var status = payload != null && payload.hasNonNull("status") ? payload.get("status").asInt() : 0;
		var key = payload != null && payload.hasNonNull("key") ? payload.get("key").asText() : null;
		var dei = key != null ? OnlyOfficeService.get().extractDocumentEditId(key) : null;
		var urlNode = payload != null ? payload.get("url") : null;
		OnlyOfficeDocument doc = null;

		if(dei == null) {
			return Response.status(Status.BAD_REQUEST).entity(OnlyOfficeResult.ERROR).build();
		}

		if(urlNode != null) {
			Ivy.log().debug("Document Id: {0}", dei.documentId());
			var url = urlNode.asText();
			var intUrl = OnlyOfficeService.get().toInternalHost(url);
			Ivy.log().debug("Converted URL to internal: original: {0} internal: {1}", url, intUrl);

			var client = OnlyOfficeService.get().absolute(intUrl);

			var rsp = client.request().get();

			if(rsp.getStatus() != 200) {
				Ivy.log().error("The document server could not load the document for editGroup ''{0}'' and documentId ''{1}'' and returned with status: {2}.", dei.editGroup(), dei.documentId(), rsp.getStatus());
				return Response.status(rsp.getStatus()).entity(OnlyOfficeResult.ERROR).build();
			}

			var stream = rsp.readEntity(InputStream.class);
			doc = OnlyOfficeDocument.builder().editGroup(dei.editGroup()).documentId(dei.documentId()).stream(stream).build();
			OnlyOfficeService.get().getOnlyOfficeDocumentHandler().callback(doc, status);
		}
		return Response.ok(OnlyOfficeResult.OK).build();

	}

	private JsonNode extractJwtPayload(String authorization) {
		JsonNode payload = null;

		var m = BEARER_PATTERN.matcher(authorization != null ? authorization : "");

		if(m.matches()) {
			var jwt = m.group(1);
			try {
				payload = OnlyOfficeService.get().extractClaimsPayload(jwt);
			} catch (Exception e) {
				Ivy.log().error("Could not extract payload from authorization header.", e);
			}
		}

		return payload;
	}
}
