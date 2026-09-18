package com.axonivy.connector.onlyoffice;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.axonivy.connector.onlyoffice.documenthandler.OnlyOfficeDocumentHandler;
import com.axonivy.connector.onlyoffice.documenthandler.OnlyOfficeIvyDocumentHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import ch.ivyteam.ivy.environment.Ivy;
import ch.ivyteam.ivy.process.call.SubProcessCallStart;
import ch.ivyteam.ivy.process.call.SubProcessSearchFilter;
import ch.ivyteam.ivy.process.call.SubProcessSearchFilter.SearchScope;
import ch.ivyteam.ivy.security.exec.Sudo;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

@ManagedBean(name = "onlyoffice")
@ApplicationScoped
public class OnlyOfficeService {
	private static final OnlyOfficeService INSTANCE = new OnlyOfficeService();
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final String VAR_TMPL = "com.axonivy.connector.onlyoffice.%s";
	private static final String CLIENT_ID = "OnlyOffice";
	private static final String ONLYOFFICE_DOCUMENT_PROVIDER_SUBPROCESS_SIGNATURE = "provideOnlyOfficeDocumentHandler()";
	private static OnlyOfficeDocumentHandler onlyOfficeDocumentHandler = null;

	public static OnlyOfficeService get() {
		return INSTANCE;
	}

	public OnlyOfficeDocumentHandler getOnlyOfficeDocumentHandler() {
		if(onlyOfficeDocumentHandler == null) {
			Ivy.log().info("Looking for a process that provides the {0} interface.", OnlyOfficeDocumentHandler.class.getCanonicalName());

			Sudo.run(() -> {

				var subProcessStartList = SubProcessCallStart.find(SubProcessSearchFilter.create()
						.setSearchScope(SearchScope.APPLICATION)
						.setSignature(ONLYOFFICE_DOCUMENT_PROVIDER_SUBPROCESS_SIGNATURE)
						.toFilter());

				Object handler = null;

				// Find subprocess
				if (CollectionUtils.isEmpty(subProcessStartList)) {
					handler = OnlyOfficeIvyDocumentHandler.get();
					Ivy.log().info("No process {0} was provided, using default handler.", ONLYOFFICE_DOCUMENT_PROVIDER_SUBPROCESS_SIGNATURE);
				}
				else {
					var subProcessStart = subProcessStartList.getFirst();
					handler = subProcessStart.call().first();
				}

				if(handler == null) {
					Ivy.log().error("Did not receive a {0}.", OnlyOfficeDocumentHandler.class.getCanonicalName());
				}
				else if(handler instanceof OnlyOfficeDocumentHandler h) {
					onlyOfficeDocumentHandler = h;
					Ivy.log().info("Using {0}: {1}.", OnlyOfficeDocumentHandler.class.getCanonicalName(), handler);
				}
				else {
					Ivy.log().error("Did not receive a {0}, instead received: {1}.", OnlyOfficeDocumentHandler.class.getCanonicalName(), handler);
				}
			});
		}
		return onlyOfficeDocumentHandler;
	}

	public WebTarget absolute(String url) {
		return Ivy.rest().client(CLIENT_ID).property(OnlyOfficeFeature.CONFIG_KEY_URL, url);
	}

	public String documentsBaseUrl() {
		return getVar("documentsBaseUrl");
	}

	public String documentServerInternalBaseUrl() {
		return getVar("documentServerInternalBaseUrl");
	}

	public String documentServerExternalBaseUrl() {
		return getVar("documentServerExternalBaseUrl");
	}

	public String onlyOfficeJwtsecret() {
		return getVar("jwtsecret");
	}

	public byte[] onlyOfficeJwtsecretBytes() {
		var secret = onlyOfficeJwtsecret();
		return secret.getBytes(StandardCharsets.UTF_8);
	}

	public String getVar(String name) {
		return Ivy.var().get(VAR_TMPL.formatted(name));
	}

	public String getDocumentsBaseUrl(String path, Object...values) {
		var base = documentsBaseUrl();
		return UriBuilder.fromUri(base).path(path).build(values).toString();
	}

	public WebTarget documentServerInternal(String path) {
		var base = documentServerInternalBaseUrl();
		var url = UriBuilder.fromUri(base).path(path).build().toString();
		return absolute(url);
	}

	public String getDocumentEditorExternalUrl() {
		var base = documentServerExternalBaseUrl();
		return UriBuilder.fromUri(base).path("web-apps/apps/api/documents/api.js").build().toString();
	}

	/**
	 * Replace schema, host and port with internal values.
	 *
	 * @param url
	 * @return
	 */
	public String toInternalHost(String url) {
		var intBase = UriBuilder
				.fromUri(documentServerInternalBaseUrl())
				.build((Object[])null);
		return UriBuilder
				.fromUri(url)
				.scheme(intBase.getScheme())
				.host(intBase.getHost())
				.port(intBase.getPort())
				.toString();
	}

	protected SecretKey cryptoKey() {
		try {
			// Create a hash with 32 bytes length to use as the key
			// (just in case the secret should ever get smaller than 32 bytes)
			var hash = MessageDigest.getInstance("SHA-256").digest(onlyOfficeJwtsecretBytes());
			return new SecretKeySpec(hash, 0, 32, "AES");
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("Error while creating key hash.", e);
		}
	}

	/**
	 * Encrypt text URL safe re-using the already defined secret used also for JWT.
	 *
	 * @param text
	 * @return
	 */
	public String encrypt(String text) {
		try {
			var cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.ENCRYPT_MODE, cryptoKey());
			byte[] encryptedBytes = cipher.doFinal(text.getBytes());
			return Base64.getUrlEncoder().withoutPadding().encodeToString(encryptedBytes);
		} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException e) {
			throw new RuntimeException("Error while encrypting.", e);
		}
	}

	/**
	 * Encrypt URL safe text re-using the already defined secret used also for JWT.
	 *
	 * @param encrypted
	 * @return
	 */
	public String decrypt(String encrypted) {
		try {
			var cipher = Cipher.getInstance("AES");
			cipher.init(Cipher.DECRYPT_MODE, cryptoKey());
			byte[] decodedBytes = Base64.getUrlDecoder().decode(encrypted);
			byte[] decryptedBytes = cipher.doFinal(decodedBytes);
			return new String(decryptedBytes);
		} catch (InvalidKeyException | NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException | BadPaddingException e) {
			throw new RuntimeException("Error while decrypting.", e);
		}
	}

	public String createToken(Map<String, Object> config) {
		var key = Keys.hmacShaKeyFor(onlyOfficeJwtsecretBytes());

		return Jwts.builder()
				.setClaims(config)
				.setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + 60 * 60 * 1000)) // 1h
				.signWith(key, SignatureAlgorithm.HS256)
				.compact();
	}

	public static record DocumentEditId(String editGroup, String documentId) {
		public static DocumentEditId create(String editGroup, String documentId) {
			return new DocumentEditId(editGroup, documentId);
		}
		public static DocumentEditId fromList(List<String> list) {
			return create(list.get(0), list.get(1));
		}
		public List<String> toList() {
			return List.of(editGroup, documentId);
		}
	}

	/**
	 * Create a document key for the document.
	 *
	 * @param editGroup any identifier, all edits in this group can occur in parallel.
	 * @param id
	 *
	 * @return
	 */
	public String createDocumentKey(String editGroup, String documentId) {
		try {
			var key = MAPPER.writeValueAsString(DocumentEditId.create(editGroup, documentId).toList());
			return encrypt(key);
		} catch (Exception e) {
			throw new RuntimeException("Could not create key for editGroup: '%s' documentId: '%s'".formatted(editGroup, documentId), e);
		}
	}

	/**
	 * Extract the information contained in a document key.
	 *
	 * @param documentKey
	 * @return
	 */
	public DocumentEditId extractDocumentEditId(String documentKey) {
		try {
			var packed = decrypt(documentKey);
			return DocumentEditId.fromList(MAPPER.readValue(packed, new TypeReference<List<String>>() {}));
		} catch (Exception e) {
			throw new RuntimeException("Could not extract from '%s'".formatted(documentKey), e);
		}
	}

	/**
	 * Call an internal Document Service command.
	 *
	 * @return
	 */
	public WebTarget command() {
		return documentServerInternal("command");
	}

	/**
	 * Call forcesave.
	 *
	 * @param key
	 * @param userdata
	 * @return
	 */
	public Response callForcesave(String key, String userdata) {
		var cmd = new LinkedHashMap<String, Object>();
		cmd.put("c", "forcesave");
		cmd.put("key", key);
		cmd.put("userdata", userdata);

		var token = createToken(cmd);

		var node = MAPPER.createObjectNode();
		node.put("token", token);
		return command().request().buildPost(Entity.entity(node, MediaType.APPLICATION_JSON)).invoke();
	}

	/**
	 * Parse a given configuration and set the typical dynamic information.
	 *
	 * This includes information about the file and edit group, the user and the signed token.
	 * Whatever is already set, will stay, so it is possible to set your own data, e.g. for the user.
	 *
	 * For a list of configuration items, see https://api.onlyoffice.com/docs/docs-api/usage-api/config/document/
	 * or the comments at the beginning of the ONLYOFFICE editor Javascript.
	 * @param editGroup
	 * @param documentId
	 * @param fileName
	 * @param configuration
	 *
	 * @return
	 */
	public String putIfAbsentAndSign(String editGroup, String documentId, String fileName, String configuration) {
		var result = "";
		try {
			var ivyUser = Ivy.session().getSessionUser();
			var lang = ivyUser.getLanguage();
			if(lang == null) {
				lang = Locale.getDefault();
			}

			var documentKey = createDocumentKey(editGroup, documentId);
			var map = StringUtils.isNotBlank(configuration) ? MAPPER.readValue(configuration, new TypeReference<Map<String, Object>>() {}) : new LinkedHashMap<String, Object>();

			putIfAbsent(map, "document", "fileType", FilenameUtils.getExtension(fileName));
			putIfAbsent(map, "document", "key", documentKey);
			putIfAbsent(map, "document", "title", fileName);
			putIfAbsent(map, "document", "url", getDocumentsBaseUrl("load/{key}", documentKey));
			putIfAbsent(map, "editorConfig", "callbackUrl", getDocumentsBaseUrl("callback"));
			putIfAbsent(map, "editorConfig", "lang", lang.getLanguage());
			putIfAbsent(map, "editorConfig", "user", "id", ivyUser.getSecurityMemberId());
			putIfAbsent(map, "editorConfig", "user", "name", ivyUser.getDisplayName());
			putIfAbsent(map, "token", createToken(map));

			result = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map);
		} catch (Exception e) {
			Ivy.log().error("An error occurred while creating an ONLYOFFICE configuration for editGroup: ''{0}'' documentId: ''{1}'' fileName: ''{2}'' configuration: ''{3}''",
					e, editGroup, documentId, fileName, configuration);
			result = e.getMessage();
		}
		return result;
	}

	/**
	 * Put the entry into a maps hierarchy and create missing maps.
	 *
	 * @param map
	 * @param params
	 */
	@SuppressWarnings("unchecked")
	public void putIfAbsent(Map<String, Object> map, Object...params) {
		if(map == null || params.length < 2) {
			throw new IllegalArgumentException("Need at least a map, a key and a value parameter, got map: %s and %d parameters.".formatted(map, params.length));
		}

		var p = Arrays.asList(params);

		var value = p.getLast();
		var path = p.subList(0, p.size() - 1);

		var idx = 0;
		var curMap = map;
		while(idx < path.size()) {
			var key = path.get(idx).toString();
			var val = curMap.get(key);
			if(idx < path.size() - 1) {
				// Not the last path component.
				if(val == null || StringUtils.isBlank(val.toString())) {
					var newMap = new LinkedHashMap<String, Object>();
					curMap.put(key, newMap);
					curMap = newMap;
				}
				else {
					curMap = (Map<String, Object>) val;
				}
			}
			else {
				// Last path component.
				if(val == null) {
					curMap.putIfAbsent(key, value);
				}
			}
			idx++;
		}
	}

	/**
	 * Provide own {@link OnlyOfficeDocumentHandler} for testing.
	 *
	 * Note: this function should only be used for testing. Since there exists only one
	 * instance of this handler, do not set to different values while multiple tests
	 * could run in parallel!
	 *
	 * @param handler
	 */
	static void setOnlyOfficeDocumentHandlerForTesting(OnlyOfficeDocumentHandler handler) {
		onlyOfficeDocumentHandler = handler;
	}
}
