package com.axonivy.connector.onlyoffice.documenthandler;

import com.axonivy.connector.onlyoffice.OnlyOfficeDocument;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.workflow.document.IDocumentService;

/**
 * Use workflow documents.
 */
public class OnlyOfficeIvyDocumentHandler implements OnlyOfficeDocumentHandler {
	private static final OnlyOfficeIvyDocumentHandler INSTANCE = new OnlyOfficeIvyDocumentHandler();

	public static OnlyOfficeIvyDocumentHandler get() {
		return INSTANCE;
	}

	@Override
	public OnlyOfficeDocument load(String editGroup, String documentId) {
		OnlyOfficeDocument document = null;
		var doc = documents().get(documentId);
		if(doc != null) {
			document = OnlyOfficeDocument.builder()
					.documentId(documentId)
					.fileName(documentId)
					.editGroup(editGroup)
					.stream(doc.read().asStream())
					.build();
		}
		return document;
	}

	@Override
	public void callback(OnlyOfficeDocument document, int status) {
		if(document != null) {
			var doc = documents().get(document.getDocumentId());
			if(doc == null) {
				throw new RuntimeException("Document does not exists: %s".formatted(document.getDocumentId()));
			}
			else {
				doc.write().withContentFrom(document.getStream());
			}
		}
	}

	protected IDocumentService documents() {
		return Ivy.wf().documents();
	}
}
