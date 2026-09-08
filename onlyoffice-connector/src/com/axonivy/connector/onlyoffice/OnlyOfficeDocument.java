package com.axonivy.connector.onlyoffice;

import java.io.InputStream;

public class OnlyOfficeDocument {
	private String editGroup;
	private String documentId;
	private String fileName;
	private String contentType;
	private InputStream stream;

	private OnlyOfficeDocument() {
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getEditGroup() {
		return editGroup;
	}

	public String getDocumentId() {
		return documentId;
	}

	public String getContentType() {
		return contentType;
	}

	public String getFileName() {
		return fileName;
	}

	public InputStream getStream() {
		return stream;
	}


	public static final class Builder {
		private final OnlyOfficeDocument document;

		private Builder() {
			document = new OnlyOfficeDocument();
		}

		public Builder editGroup(String editGroup) {
			document.editGroup = editGroup;
			return this;
		}

		public Builder documentId(String documentId) {
			document.documentId = documentId;
			return this;
		}

		public Builder fileName(String fileName) {
			document.fileName = fileName;
			return this;
		}

		public Builder contentType(String contentType) {
			document.contentType = contentType;
			return this;
		}

		public Builder stream(InputStream stream) {
			document.stream = stream;
			return this;
		}

		public OnlyOfficeDocument build() {
			// If no documentId is defined, then use fileName.
			if(document.documentId == null) {
				document.documentId = document.fileName;
			}
			return document;
		}
	}
}
