package com.axonivy.connector.onlyoffice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

class OnlyOfficeDocumentTest {

	@Test
	void build_usesFileNameAsDocumentIdWhenDocumentIdIsMissing() {
		var doc = OnlyOfficeDocument.builder()
				.editGroup("group-1")
				.fileName("report.pdf")
				.contentType("application/pdf")
				.stream(new ByteArrayInputStream("content".getBytes()))
				.build();

		assertThat(doc.getEditGroup()).isEqualTo("group-1");
		assertThat(doc.getDocumentId()).isEqualTo("report.pdf");
		assertThat(doc.getFileName()).isEqualTo("report.pdf");
		assertThat(doc.getContentType()).isEqualTo("application/pdf");
		assertThat(doc.getStream()).isNotNull();
	}

	@Test
	void build_keepsExplicitDocumentId() {
		var doc = OnlyOfficeDocument.builder()
				.editGroup("group-1")
				.documentId("doc-123")
				.fileName("report.pdf")
				.contentType("application/pdf")
				.stream(new ByteArrayInputStream("content".getBytes()))
				.build();

		assertThat(doc.getEditGroup()).isEqualTo("group-1");
		assertThat(doc.getDocumentId()).isEqualTo("doc-123");
		assertThat(doc.getFileName()).isEqualTo("report.pdf");
		assertThat(doc.getContentType()).isEqualTo("application/pdf");
		assertThat(doc.getStream()).isNotNull();
	}

	@Test
	void build_allowsNullStream() {
		var doc = OnlyOfficeDocument.builder()
				.editGroup("group-1")
				.documentId("doc-123")
				.fileName("report.pdf")
				.contentType("application/pdf")
				.stream(null)
				.build();

		assertThat(doc.getStream()).isNull();
	}
}
