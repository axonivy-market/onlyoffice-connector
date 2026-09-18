package com.axonivy.connector.onlyoffice;

import java.io.ByteArrayInputStream;

import javax.annotation.security.PermitAll;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Simulate the OnlyOffice Service.
 */
@Path("/test")
public class OnlyOfficeTestResource {

	@GET
	@Path("document/{random}")
	@PermitAll
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response getDocument(@PathParam("random") String random) {
		var dei = OnlyOfficeService.get().extractDocumentEditId(random);
		if("test".equals(dei.editGroup()) && "123".equals(dei.documentId())) {
			// Send a dummy document containing the random path part as the content.
			return Response.ok(new ByteArrayInputStream("Content:%s".formatted(random).getBytes()))
					.header("Content-Disposition", "attachment; filename=\"test.txt\"")
					.build();
		}
		else {
			return Response.status(Response.Status.NOT_FOUND).build();
		}
	}
}
