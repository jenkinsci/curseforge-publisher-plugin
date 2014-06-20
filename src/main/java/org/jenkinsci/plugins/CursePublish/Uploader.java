package org.jenkinsci.plugins.CursePublish;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Uploader {
	public enum ReleaseType {
		RELEASE, BETA, ALPHA
	}

	private final HttpClient httpclient;

	public Uploader() {
		this(new DefaultHttpClient());
	}

	public Uploader(HttpClient client) {
		this.httpclient = client;
	}

	public JSONArray getModVersions(String game, String apiKey)
			throws IOException {
		HttpGet getVersions = new HttpGet("http://" + game
				+ ".curseforge.com/api/game/versions");
		getVersions.addHeader("X-Api-Token", apiKey);
		HttpResponse versionResponse = httpclient.execute(getVersions);
		JSONArray json;
		String body = null;
		Scanner scanner = null;
		try {
			scanner = new Scanner(versionResponse.getEntity().getContent());
			body = scanner.useDelimiter("\\A").next();
			json = new JSONArray(body);
		} catch (JSONException ex) {
			throw new IOException("Parse error: " + body);
		} finally {
			if (scanner != null)
				scanner.close();
		}
		return json;
	}

	public long uploadMod(String game, String apiKey, long modId,
			String filename, InputStream file, String changelog,
			ReleaseType releaseType, String... gameVersions) throws IOException {
		List<Long> versions = new ArrayList<Long>();
		JSONArray actualVersions = getModVersions(game, apiKey);
		for (String gameVersion : gameVersions) {
			boolean matched = false;
			for (int i = 0; i < actualVersions.length(); i++) {
				JSONObject serverVersion = actualVersions.getJSONObject(i);
				if (gameVersion.equals(serverVersion.get("id"))
						|| gameVersion.equals(serverVersion.get("name"))
						|| gameVersion.equals(serverVersion.get("slug"))) {
					versions.add(serverVersion.getLong("id"));
					matched = true;
					break;
				}
			}

			if (!matched) {
				StringBuilder validVersions = new StringBuilder();
				for (int i = 0; i < actualVersions.length(); i++) {
					if (i != 0) {
						validVersions.append(",");
					}
					validVersions.append(actualVersions.getJSONObject(i).get(
							"name"));
				}

				throw new IOException("Invalid game version: " + gameVersion
						+ ". (Valid versions: " + validVersions.toString()
						+ ")");
			}
		}

		HttpPost httpPost = new HttpPost("http://" + game
				+ ".curseforge.com/api/projects/" + modId + "/upload-file");
		httpPost.addHeader("X-Api-Token", apiKey);

		JSONObject metadata = new JSONObject();
		if (changelog == null)
			changelog = "";
		metadata.put("changelog", changelog);
		metadata.put("releaseType", releaseType.toString().toLowerCase());
		metadata.put("gameVersions", versions);

		InputStreamBody uploadFilePart = new InputStreamBody(file, filename);
		MultipartEntity reqEntity = new MultipartEntity();
		reqEntity.addPart("file", uploadFilePart);
		reqEntity.addPart("metadata", new StringBody(metadata.toString()));
		httpPost.setEntity(reqEntity);

		HttpResponse response = httpclient.execute(httpPost);
		if (response.getStatusLine().getStatusCode() != 200) {
			throw new IOException("Got status code "
					+ response.getStatusLine().getStatusCode());
		}
		JSONObject json;
		String body = null;
		Scanner scanner = null;
		try {
			scanner = new Scanner(response.getEntity().getContent());
			json = new JSONObject(scanner.useDelimiter("\\A").next());
		} catch (JSONException ex) {
			throw new IOException("Parse error: " + body);
		} finally {
			if (scanner != null)
				scanner.close();
		}

		return json.getLong("id");
	}
}
