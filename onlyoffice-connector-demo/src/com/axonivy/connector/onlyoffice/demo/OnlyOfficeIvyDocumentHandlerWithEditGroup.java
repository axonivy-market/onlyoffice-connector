package com.axonivy.connector.onlyoffice.demo;

import com.axonivy.connector.onlyoffice.OnlyOfficeDocument;
import com.axonivy.connector.onlyoffice.documenthandler.OnlyOfficeIvyDocumentHandler;

public class OnlyOfficeIvyDocumentHandlerWithEditGroup extends OnlyOfficeIvyDocumentHandler {
	private static final OnlyOfficeIvyDocumentHandlerWithEditGroup INSTANCE = new OnlyOfficeIvyDocumentHandlerWithEditGroup();

	public static OnlyOfficeIvyDocumentHandlerWithEditGroup get() {
		return INSTANCE;
	}

	@Override
	public void callback(OnlyOfficeDocument document, int status) {
		super.callback(document, status);
		switch(status) {
		case 2: /* final save */
		case 4: /* final close */
			DemoService.get().clearEditGroup(document.getDocumentId());
			break;
		default:
			break;

		}
	}
}
