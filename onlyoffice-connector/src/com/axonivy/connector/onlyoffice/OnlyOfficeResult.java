package com.axonivy.connector.onlyoffice;

public class OnlyOfficeResult {
	private int error = 0;

	public static final OnlyOfficeResult OK = OnlyOfficeResult.create(0);

	public static OnlyOfficeResult create(int error) {
		var result = new OnlyOfficeResult();
		result.setError(error);
		return result;
	}

	public int getError() {
		return error;
	}

	public void setError(int error) {
		this.error = error;
	}
}
