package com.axonivy.connector.onlyoffice.documenthandler;

import com.axonivy.connector.onlyoffice.OnlyOfficeDocument;

public interface OnlyOfficeDocumentHandler {
	/**
	 * Called when the document should be loaded.
	 *
	 * @param editGroup
	 * @param documentId
	 * @return
	 */
	public OnlyOfficeDocument load(String editGroup, String documentId);

	/**
	 * Called when users leave the editor.
	 *
	 * For a list of status codes see <a href="https://api.onlyoffice.com/docs/docs-api/usage-api/callback-handler/#status*">ONLYOFFIC callback handler documentation</a>.
	 * You should at least handle status 2 (last user closed and save is necessary) and 6 (intermediate forcesave). Depending on your simultaneous editing situation,
	 * you might also want to care about status 4 (last user closed and no save is necessary).
	 *
	 * @param document
	 * @param status
	 */
	public void callback(OnlyOfficeDocument document, int status);
}
