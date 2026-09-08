package com.axonivy.connector.onlyoffice;

import java.io.InputStream;
import java.util.Set;

import javax.annotation.security.PermitAll;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;

import ch.ivyteam.ivy.environment.Ivy;

@Path("/documents")
public class OnlyOfficeResource {
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
	public Response loadDocument(@Context HttpServletRequest rq, @PathParam("key") String key) {
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

	@POST
	@Path("callback")
	@PermitAll
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Response callback(@Context HttpServletRequest rq, JsonNode payload) {
		Ivy.log().debug("Callback for document: {0}", payload);

		var status = payload.get("status").asInt();
		var key = payload.get("key").asText();
		var dei = OnlyOfficeService.get().extractDocumentEditId(key);
		Ivy.log().debug("Document Id: {0}", dei.documentId());
		var urlNode = payload.get("url");
		OnlyOfficeDocument doc = null;

		if(urlNode != null) {
			var url = urlNode.asText();
			var intUrl = OnlyOfficeService.get().toInternalUrl(url);
			Ivy.log().debug("Converted URL to internal: original: {0} internal: {1}", url, intUrl);

			var client = OnlyOfficeService.get().absolute(intUrl);

			var stream = client.request().get().readEntity(InputStream.class);

			doc = OnlyOfficeDocument.builder().editGroup(dei.editGroup()).documentId(dei.documentId()).stream(stream).build();
		}

		OnlyOfficeService.get().getOnlyOfficeDocumentHandler().callback(doc, status);
		return Response.ok(OnlyOfficeResult.OK).build();
	}
}
