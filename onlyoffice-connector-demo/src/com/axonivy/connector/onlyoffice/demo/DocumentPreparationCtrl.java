package com.axonivy.connector.onlyoffice.demo;

import java.io.IOException;
import java.util.UUID;

import org.primefaces.event.FileUploadEvent;

import com.axonivy.connector.onlyoffice.OnlyOfficeService;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.workflow.document.IDocument;
import ch.ivyteam.ivy.workflow.document.IDocumentService;

public class DocumentPreparationCtrl {
	private String editGroup;
	private IDocument document;

	public DocumentPreparationCtrl(String editGroup) {
		this.editGroup = editGroup;
	}

	public String getEditGroup() {
		return editGroup;
	}

	public IDocument getDocument() {
		return document;
	}

	public void handleFileUpload(FileUploadEvent event) throws IOException {
		var uploaded = event.getFile();
		var name = uploaded.getFileName();

		document = documents().get(name);

		if(document == null) {
			document = documents().add(name);
		}

		document.write().withContentFrom(uploaded.getInputStream());
	}

	public void callForceSave() {
		var key = OnlyOfficeService.get().createDocumentKey(editGroup, document.uuid());
		OnlyOfficeService.get().callForcesave(key, UUID.randomUUID().toString());
	}

	protected IDocumentService documents() {
		return Ivy.wfCase().documents();
	}
}

