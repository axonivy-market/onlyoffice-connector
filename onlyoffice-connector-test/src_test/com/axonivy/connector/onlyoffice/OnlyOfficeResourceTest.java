package com.axonivy.connector.onlyoffice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.ws.rs.client.WebTarget;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.axonivy.connector.onlyoffice.documenthandler.OnlyOfficeDocumentHandler;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import ch.ivyteam.ivy.environment.AppFixture;
import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.environment.IvyTest;

@IvyTest(enableWebServer = true)
public class OnlyOfficeResourceTest {
	private static final String TEST_SECRET = "AtLeast32BytesOfSecret0123456789";
	protected static final OnlyOfficeDocumentHandler TEST_HANDLER = new OnlyOfficeDocumentHandler() {
		@Override
		public OnlyOfficeDocument load(String editGroup, String documentId) {
			OnlyOfficeDocument doc = null;
			if(("test").equals(editGroup) && ("123").equals(documentId)) {
				doc = OnlyOfficeDocument.builder()
						.editGroup(editGroup)
						.documentId(documentId)
						.fileName("test.txt")
						.stream(new ByteArrayInputStream("EditGroup:%s,DocumentId:%s".formatted(editGroup, documentId).getBytes()))
						.build();
			}

			return doc;
		}

		@Override
		public void callback(OnlyOfficeDocument document, int status) {

			if(!"test".equals(document.getEditGroup())) {
				throw new RuntimeException("Received wrong edit group '%s'.".formatted(document.getEditGroup()));
			}


			var is123 = "123".equals(document.getDocumentId());
			var is124 = "124".equals(document.getDocumentId());

			if(!is123 && !is124) {
				throw new RuntimeException("Received wrong document id '%s'.".formatted(document.getDocumentId()));
			}

			String content = null;
			try {
				content = new String(document.getStream().readAllBytes());
				if(is123 && !"Content:%s".formatted(OnlyOfficeService.get().createDocumentKey("test", "123")).equals(content)) {
					throw new RuntimeException("Received wrong content '%s'.".formatted(content));
				}
				else if(is124 && !"".equals(content)) {
					throw new RuntimeException("Received wrong content '%s'.".formatted(content));

				}
			} catch (Exception e) {
				throw new RuntimeException("Exception reading content.", e);
			}
		}
	};

	@BeforeAll
	public static void setOnlyOfficeDocumentHandler() {
		OnlyOfficeService.setOnlyOfficeDocumentHandlerForTesting(TEST_HANDLER);
	}

	protected WebTarget client() {
		return Ivy.rest().client("OnlyOfficeTestCallback");
	}

	@Test
	public void testLoadDocumentFoundUnsigned() throws IOException {
		var rsp = new OnlyOfficeResource().loadDocument(null, null, OnlyOfficeService.get().createDocumentKey("test", "123"));
		assertThat(rsp.getStatus()).isEqualTo(200);
		var content = new String(((InputStream)rsp.getEntity()).readAllBytes());
		assertThat(content).isEqualTo("EditGroup:test,DocumentId:123");
	}

	@Test
	public void testLoadDocumentNotFoundEditGroupUnsigned() {
		var rsp = new OnlyOfficeResource().loadDocument(null, null, OnlyOfficeService.get().createDocumentKey("foo", "123"));
		assertThat(rsp.getStatus()).isEqualTo(404);
	}

	@Test
	public void testLoadDocumentNotFoundDocumentIdUnsigned() {
		var rsp = new OnlyOfficeResource().loadDocument(null, null, OnlyOfficeService.get().createDocumentKey("test", "124"));
		assertThat(rsp.getStatus()).isEqualTo(404);
	}

	@Test
	public void testCallbackUnsigned(AppFixture fix) {
		var internalBaseUrl = client().getUri().toString();
		fix.var("com.axonivy.connector.onlyoffice.documentServerInternalBaseUrl", internalBaseUrl);

		var key = OnlyOfficeService.get().createDocumentKey("test", "123");

		var payload = JsonNodeFactory.instance.objectNode();
		payload.put("status", "2");
		payload.put("key", OnlyOfficeService.get().createDocumentKey("test", "123"));
		payload.put("url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString());

		var rsp = new OnlyOfficeResource().callback(null, null, payload);
		assertThat(rsp.getStatus()).isEqualTo(200);
	}

	@Test
	public void testCallbackWrongKeyUnsigned(AppFixture fix) {
		var internalBaseUrl = client().getUri().toString();
		fix.var("com.axonivy.connector.onlyoffice.documentServerInternalBaseUrl", internalBaseUrl);

		var key = OnlyOfficeService.get().createDocumentKey("test", "124");

		var payload = JsonNodeFactory.instance.objectNode();
		payload.put("status", "2");
		payload.put("key", OnlyOfficeService.get().createDocumentKey("test", "124"));
		payload.put("url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString());

		var rsp = new OnlyOfficeResource().callback(null, null, payload);
		assertThat(rsp.getStatus()).isEqualTo(404);
	}


	protected String createBearerToken(Map<String, Object> claims) {
		return "Bearer %s".formatted(OnlyOfficeService.get().createToken(claims));
	}

	@Test
	public void testLoadDocumentFound(AppFixture fix) throws IOException {
		fix.var("com.axonivy.connector.onlyoffice.jwtsecret", TEST_SECRET);

		var rsp = new OnlyOfficeResource().loadDocument(
				null,
				createBearerToken(Map.of(
						"payload",	Map.of(
								"url", "https://someurl/bla/%s?dummy=123".formatted(OnlyOfficeService.get().createDocumentKey("test", "123"))))),
				OnlyOfficeService.get().createDocumentKey("test", "123"));

		assertThat(rsp.getStatus()).isEqualTo(200);
		var content = new String(((InputStream)rsp.getEntity()).readAllBytes());
		assertThat(content).isEqualTo("EditGroup:test,DocumentId:123");
	}

	@Test
	public void testLoadDocumentWrongSigned(AppFixture fix) throws IOException {
		fix.var("com.axonivy.connector.onlyoffice.jwtsecret", TEST_SECRET);

		var rsp = new OnlyOfficeResource().loadDocument(
				null,
				createBearerToken(Map.of(
						"payload", Map.of(
								"url", "https://someurl/bla?dummy=123"))),
				OnlyOfficeService.get().createDocumentKey("test", "123"));

		assertThat(rsp.getStatus()).isEqualTo(400);
	}

	@Test
	public void testLoadDocumentNotFoundEditGroup(AppFixture fix) {
		fix.var("com.axonivy.connector.onlyoffice.jwtsecret", TEST_SECRET);

		var rsp = new OnlyOfficeResource().loadDocument(
				null,
				createBearerToken(Map.of(
						"payload", Map.of(
								"url", "https://someurl/bla/%s".formatted(OnlyOfficeService.get().createDocumentKey("foo", "123"))))),
				OnlyOfficeService.get().createDocumentKey("foo", "123"));

		assertThat(rsp.getStatus()).isEqualTo(404);
	}

	@Test
	public void testLoadDocumentNotFoundDocumentId(AppFixture fix) {
		fix.var("com.axonivy.connector.onlyoffice.jwtsecret", TEST_SECRET);

		var rsp = new OnlyOfficeResource().loadDocument(
				null,
				createBearerToken(Map.of(
						"payload", Map.of(
								"url", "https://someurl/bla/%s".formatted(OnlyOfficeService.get().createDocumentKey("test", "124"))))),
				OnlyOfficeService.get().createDocumentKey("test", "124"));

		assertThat(rsp.getStatus()).isEqualTo(404);
	}

	@Test
	public void testCallback(AppFixture fix) {
		fix.var("com.axonivy.connector.onlyoffice.jwtsecret", TEST_SECRET);
		var internalBaseUrl = client().getUri().toString();
		fix.var("com.axonivy.connector.onlyoffice.documentServerInternalBaseUrl", internalBaseUrl);

		var key = OnlyOfficeService.get().createDocumentKey("test", "123");

		var payload = JsonNodeFactory.instance.objectNode();
		payload.put("status", "2");
		payload.put("key", OnlyOfficeService.get().createDocumentKey("test", "123"));
		payload.put("url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString());

		var rsp = new OnlyOfficeResource().callback(
				null,
				createBearerToken(Map.of(
						"payload", Map.of(
								"status", "2",
								"key", OnlyOfficeService.get().createDocumentKey("test", "123"),
								"url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString()))),
				payload);

		assertThat(rsp.getStatus()).isEqualTo(200);
	}

	@Test
	public void testCallbackWrongKey(AppFixture fix) {
		fix.var("com.axonivy.connector.onlyoffice.jwtsecret", TEST_SECRET);
		var internalBaseUrl = client().getUri().toString();
		fix.var("com.axonivy.connector.onlyoffice.documentServerInternalBaseUrl", internalBaseUrl);

		var key = OnlyOfficeService.get().createDocumentKey("test", "124");

		var payload = JsonNodeFactory.instance.objectNode();
		payload.put("status", "2");
		payload.put("key", OnlyOfficeService.get().createDocumentKey("test", "124"));
		payload.put("url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString());

		var rsp = new OnlyOfficeResource().callback(
				null,
				createBearerToken(Map.of(
						"payload", Map.of(
								"status", "2",
								"key", OnlyOfficeService.get().createDocumentKey("test", "124"),
								"url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString()))),
				payload);

		assertThat(rsp.getStatus()).isEqualTo(404);
	}
}
