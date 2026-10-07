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
				if(is123 && !isContentOfKey(content, "test", "123")) {
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

	private static boolean isContentOfKey(String content, String editGroup, String documentId) {
		var prefix = "Content:";
		if(!content.startsWith(prefix)) {
			return false;
		}
		var dei = OnlyOfficeService.get().extractDocumentEditId(content.substring(prefix.length()));
		return editGroup.equals(dei.editGroup()) && documentId.equals(dei.documentId());
	}

	@BeforeAll
	public static void setOnlyOfficeDocumentHandler() {
		OnlyOfficeService.setOnlyOfficeDocumentHandlerForTesting(TEST_HANDLER);
	}

	protected WebTarget client() {
		return Ivy.rest().client("OnlyOfficeTestCallback");
	}

	@Test
	public void loadDocument_withoutSignature_returnsDocument() throws IOException {
		var rsp = new OnlyOfficeResource().loadDocument(null, null, OnlyOfficeService.get().createDocumentKey("test", "123"));
		assertThat(rsp.getStatus()).isEqualTo(200);
		var content = new String(((InputStream)rsp.getEntity()).readAllBytes());
		assertThat(content).isEqualTo("EditGroup:test,DocumentId:123");
	}

	@Test
	public void loadDocument_withoutSignatureAndUnknownEditGroup_returnsNotFound() {
		var rsp = new OnlyOfficeResource().loadDocument(null, null, OnlyOfficeService.get().createDocumentKey("foo", "123"));
		assertThat(rsp.getStatus()).isEqualTo(404);
	}

	@Test
	public void loadDocument_withoutSignatureAndUnknownDocumentId_returnsNotFound() {
		var rsp = new OnlyOfficeResource().loadDocument(null, null, OnlyOfficeService.get().createDocumentKey("test", "124"));
		assertThat(rsp.getStatus()).isEqualTo(404);
	}

	@Test
	public void callback_withoutSignature_returnsOk(AppFixture fix) {
		var internalBaseUrl = client().getUri().toString();
		fix.var("com.axonivy.connector.onlyoffice.documentServerInternalBaseUrl", internalBaseUrl);

		var key = OnlyOfficeService.get().createDocumentKey("test", "123");

		var payload = JsonNodeFactory.instance.objectNode();
		payload.put("status", "2");
		payload.put("key", key);
		payload.put("url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString());

		var rsp = new OnlyOfficeResource().callback(null, null, payload);
		assertThat(rsp.getStatus()).isEqualTo(200);
	}

	@Test
	public void callback_withoutSignatureAndUnknownDocumentKey_returnsNotFound(AppFixture fix) {
		var internalBaseUrl = client().getUri().toString();
		fix.var("com.axonivy.connector.onlyoffice.documentServerInternalBaseUrl", internalBaseUrl);

		var key = OnlyOfficeService.get().createDocumentKey("test", "124");

		var payload = JsonNodeFactory.instance.objectNode();
		payload.put("status", "2");
		payload.put("key", key);
		payload.put("url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString());

		var rsp = new OnlyOfficeResource().callback(null, null, payload);
		assertThat(rsp.getStatus()).isEqualTo(404);
	}


	protected String createBearerToken(Map<String, Object> claims) {
		return "Bearer %s".formatted(OnlyOfficeService.get().createToken(claims));
	}

	@Test
	public void loadDocument_withValidSignature_returnsDocument(AppFixture fix) throws IOException {
		fix.var("com.axonivy.connector.onlyoffice.jwtsecret", TEST_SECRET);
		var key = OnlyOfficeService.get().createDocumentKey("test", "123");

		var rsp = new OnlyOfficeResource().loadDocument(
				null,
				createBearerToken(Map.of(
						"payload",	Map.of(
								"url", "https://someurl/bla/%s?dummy=123".formatted(key)))),
				key);

		assertThat(rsp.getStatus()).isEqualTo(200);
		var content = new String(((InputStream)rsp.getEntity()).readAllBytes());
		assertThat(content).isEqualTo("EditGroup:test,DocumentId:123");
	}

	@Test
	public void loadDocument_withMismatchingSignedUrl_returnsBadRequest(AppFixture fix) throws IOException {
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
	public void loadDocument_withValidSignatureAndUnknownEditGroup_returnsNotFound(AppFixture fix) {
		fix.var("com.axonivy.connector.onlyoffice.jwtsecret", TEST_SECRET);
		var key = OnlyOfficeService.get().createDocumentKey("foo", "123");

		var rsp = new OnlyOfficeResource().loadDocument(
				null,
				createBearerToken(Map.of(
						"payload", Map.of(
								"url", "https://someurl/bla/%s".formatted(key)))),
				key);

		assertThat(rsp.getStatus()).isEqualTo(404);
	}

	@Test
	public void loadDocument_withValidSignatureAndUnknownDocumentId_returnsNotFound(AppFixture fix) {
		fix.var("com.axonivy.connector.onlyoffice.jwtsecret", TEST_SECRET);
		var key = OnlyOfficeService.get().createDocumentKey("test", "124");

		var rsp = new OnlyOfficeResource().loadDocument(
				null,
				createBearerToken(Map.of(
						"payload", Map.of(
								"url", "https://someurl/bla/%s".formatted(key)))),
				key);

		assertThat(rsp.getStatus()).isEqualTo(404);
	}

	@Test
	public void callback_withValidSignature_returnsOk(AppFixture fix) {
		fix.var("com.axonivy.connector.onlyoffice.jwtsecret", TEST_SECRET);
		var internalBaseUrl = client().getUri().toString();
		fix.var("com.axonivy.connector.onlyoffice.documentServerInternalBaseUrl", internalBaseUrl);

		var key = OnlyOfficeService.get().createDocumentKey("test", "123");

		var payload = JsonNodeFactory.instance.objectNode();
		payload.put("status", "2");
		payload.put("key", key);
		payload.put("url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString());

		var rsp = new OnlyOfficeResource().callback(
				null,
				createBearerToken(Map.of(
						"payload", Map.of(
								"status", "2",
								"key", key,
								"url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString()))),
				payload);

		assertThat(rsp.getStatus()).isEqualTo(200);
	}

	@Test
	public void callback_withValidSignatureAndUnknownDocumentKey_returnsNotFound(AppFixture fix) {
		fix.var("com.axonivy.connector.onlyoffice.jwtsecret", TEST_SECRET);
		var internalBaseUrl = client().getUri().toString();
		fix.var("com.axonivy.connector.onlyoffice.documentServerInternalBaseUrl", internalBaseUrl);

		var key = OnlyOfficeService.get().createDocumentKey("test", "124");

		var payload = JsonNodeFactory.instance.objectNode();
		payload.put("status", "2");
		payload.put("key", key);
		payload.put("url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString());

		var rsp = new OnlyOfficeResource().callback(
				null,
				createBearerToken(Map.of(
						"payload", Map.of(
								"status", "2",
								"key", key,
								"url", client().path("test/document/{random}").resolveTemplate("random", key).getUri().toString()))),
				payload);

		assertThat(rsp.getStatus()).isEqualTo(404);
	}
}
