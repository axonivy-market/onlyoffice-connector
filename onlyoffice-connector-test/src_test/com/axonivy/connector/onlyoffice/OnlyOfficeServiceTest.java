package com.axonivy.connector.onlyoffice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.ivyteam.ivy.environment.IvyTest;

@IvyTest
class OnlyOfficeServiceTest {
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private final OnlyOfficeService service = OnlyOfficeService.get();


	@Test
	void createDocumentKey_andExtractDocumentEditId_roundTrip() {
		var documentId = "doc-123";
		var editGroup = "group-a";

		var key = service.createDocumentKey(editGroup, documentId);

		assertNotNull(key);
		var dei = service.extractDocumentEditId(key);

		assertThat(dei.toList()).containsExactly(editGroup, documentId);
		assertThat(dei.editGroup()).isEqualTo(editGroup);
		assertThat(dei.documentId()).isEqualTo(documentId);
	}

	@Test
	void extractDocumentEditId_withInvalidKey_throwsException() {
		assertThrows(RuntimeException.class, () -> service.extractDocumentEditId("not-a-valid-key"));
	}

	@Test
	void putIfAbsent_withInvalidParameters() {
		assertThrows(IllegalArgumentException.class, () -> service.putIfAbsent(null));
		assertThrows(IllegalArgumentException.class, () -> service.putIfAbsent(new LinkedHashMap<>()));
		assertThrows(IllegalArgumentException.class, () -> service.putIfAbsent(new LinkedHashMap<>(), "key"));
		assertThrows(IllegalArgumentException.class, () -> service.putIfAbsent(null, "key"));
		assertThrows(IllegalArgumentException.class, () -> service.putIfAbsent(null, "key", "value"));
	}

	@Test
	void putIfAbsent_withValidParameters() throws JsonProcessingException {
		var map = new LinkedHashMap<String, Object>();

		assertThat(MAPPER.writer().writeValueAsString(map)).isEqualTo("{}");

		service.putIfAbsent(map, "document", "fileType", "file-type");
		assertThat(MAPPER.writer().writeValueAsString(map)).isEqualTo(compact("""
				{
					"document": {
						"fileType": "file-type"
					}
				}
				"""));

		service.putIfAbsent(map, "document", "key", "document-key");
		assertThat(MAPPER.writer().writeValueAsString(map)).isEqualTo(compact("""
				{
					"document": {
						"fileType": "file-type",
						"key": "document-key"
					}
				}
				"""));

		service.putIfAbsent(map, "editorConfig", "callbackUrl", "callback-url");
		assertThat(MAPPER.writer().writeValueAsString(map)).isEqualTo(compact("""
				{
					"document": {
						"fileType": "file-type",
						"key": "document-key"
					},
					"editorConfig": {
						"callbackUrl": "callback-url"
					}
				}
				"""));

		service.putIfAbsent(map, "editorConfig", "user", "id", "user-id");
		assertThat(MAPPER.writer().writeValueAsString(map)).isEqualTo(compact("""
				{
					"document": {
						"fileType": "file-type",
						"key": "document-key"
					},
					"editorConfig": {
						"callbackUrl": "callback-url",
						"user": {
							"id": "user-id"
						}
					}
				}
				"""));

		service.putIfAbsent(map, "editorConfig", "user", "name", "user-name");
		assertThat(MAPPER.writer().writeValueAsString(map)).isEqualTo(compact("""
				{
					"document": {
						"fileType": "file-type",
						"key": "document-key"
					},
					"editorConfig": {
						"callbackUrl": "callback-url",
						"user": {
							"id": "user-id",
							"name": "user-name"
						}
					}
				}
				"""));

		service.putIfAbsent(map, "token", "token");
		assertThat(MAPPER.writer().writeValueAsString(map)).isEqualTo(compact("""
				{
					"document": {
						"fileType": "file-type",
						"key": "document-key"
					},
					"editorConfig": {
						"callbackUrl": "callback-url",
						"user": {
							"id": "user-id",
							"name": "user-name"
						}
					},
					"token": "token"
				}
				"""));
	}

	protected String compact(String json) {
		return json.replaceAll("\\s", "");
	}
}
