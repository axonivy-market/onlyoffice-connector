package com.axonivy.connector.onlyoffice.demo;

import java.util.UUID;

import ch.ivyteam.ivy.application.IApplication;
import ch.ivyteam.ivy.data.cache.IDataCache;

public class DemoService {
	private static final DemoService INSTANCE = new DemoService();
	private static final String CACHE_GROUP = "onlyoffice.document";
	private static final String EDITGROUP = "editGroup";

	public static DemoService get() {
		return INSTANCE;
	}

	/**
	 * Store the edit group in application cache.
	 *
	 * @param documentId
	 * @return
	 */
	public String getEditGroup(String documentId) {
		// Only for demonstration. In a cluster environment, a more elaborate locking mechanism is needed.
		synchronized (DemoService.class) {
			String editGroup = null;
			var key = "%s.%s".formatted(EDITGROUP, documentId);
			var entry = cache().getEntry(CACHE_GROUP, key);
			if(entry == null || !entry.isValid()) {
				editGroup = UUID.randomUUID().toString();
				cache().setEntry(CACHE_GROUP, key, editGroup);
			}
			else {
				editGroup = (String) entry.getValue();
			}
			return editGroup;
		}
	}

	/**
	 * Clear the edit group from application cache.
	 *
	 * @param documentId
	 */
	public void clearEditGroup(String documentId) {
		// Only for demonstration. In a cluster environment, a more elaborate locking mechanism is needed.
		synchronized (DemoService.class) {
			var key = "%s.%s".formatted(EDITGROUP, documentId);
			var entry = cache().getEntry(CACHE_GROUP, key);
			if(entry != null) {
				entry.invalidate();
			}
		}
	}

	public IDataCache cache() {
		return IDataCache.of(IApplication.current());
	}
}
