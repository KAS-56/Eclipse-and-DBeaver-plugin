package ru.tensor.explain.dbeaver.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.Optional;
import java.util.UUID;

import org.eclipse.core.runtime.ILog;
import org.eclipse.jface.preference.IPreferenceStore;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ru.tensor.explain.dbeaver.ExplainPostgreSQLPlugin;
import ru.tensor.explain.dbeaver.preferences.PreferenceConstants;

public class ExplainAPI implements IExplainAPI {

	private static final ILog log = ExplainPostgreSQLPlugin.getDefault().getLog();
	private String EXPLAIN_URL = "https://explain.tensor.ru";
	private static final String API_BEAUTIFIER = "/beautifier-api";
	private static final String API_PLANARCHIVE = "/explain";
	private final IPreferenceStore store = ExplainPostgreSQLPlugin.getDefault().getPreferenceStore();

	private String getExplainURL() {
		return store.getString(PreferenceConstants.P_SITE);
	}

	@Override
	public void beautifier(String sql, Consumer<String> callback) {
		EXPLAIN_URL = getExplainURL();
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("query_src", sql);

		log.info("POST JSON: " + jsonObject);

		HttpClient client = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.build();

		HttpRequest request = HttpRequest.newBuilder()
				.header("content-type", "application/json")
				.header("user-agent", ExplainPostgreSQLPlugin.versionString)
				.POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString()))
				.timeout(Duration.ofSeconds(30))
				.uri(URI.create(EXPLAIN_URL + API_BEAUTIFIER))
				.build();

		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(HttpResponse::body)
				.thenApply((String res) -> {
					log.info("Beautifier result: " + res);
					JsonElement jsonElement = JsonParser.parseString(res);
					JsonObject object = jsonElement.getAsJsonObject();
					return object.get("btf_query_text").getAsString();
				}).handle((result, ex) -> {
					if (ex != null) {
						String error = ex.getMessage();
						log.error(error, ex);
						return "Error: " + error;
					} else {
						return result;
					}
				}).thenAccept(callback::accept);

	}

	@Override
	public void plan_archive(String plan, String query, Consumer<String> callback) {
		EXPLAIN_URL = getExplainURL();

		String uuid = UUID.randomUUID().toString();
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("name", uuid);
		jsonObject.addProperty("group", uuid);
		jsonObject.addProperty("parent", uuid);
		jsonObject.addProperty("plan", plan);
		jsonObject.addProperty("query", query);
		jsonObject.addProperty("private", "false");

		log.info("POST JSON: " + jsonObject);

		HttpClient client = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.build();

		HttpRequest request = HttpRequest.newBuilder()
				.header("content-type", "application/json")
				.header("user-agent", ExplainPostgreSQLPlugin.versionString)
				.POST(HttpRequest.BodyPublishers.ofString(jsonObject.toString()))
				.timeout(Duration.ofSeconds(30))
				.uri(URI.create(EXPLAIN_URL + API_PLANARCHIVE))
				.build();

		client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				.thenApply(response -> {
					int statusCode = response.statusCode();
					log.info("Received response with status code: " + statusCode);
					if (statusCode == 302) {
						Optional<String> location = response.headers().firstValue("Location");
						if (location.isPresent()) {
							log.info("Explain result location: " + location.get());
							return EXPLAIN_URL + location.get();
						} else {
							log.error("Location header not found");
							return EXPLAIN_URL;
						}
					} else {
						String body = response.body();
						if (body.isBlank()) {
							log.error("Received unexpected response code with empty response body");
						} else {
							log.error("Received unexpected response code: " + body);
						}
						return EXPLAIN_URL;
					}
				}).handle((result, ex) -> {
					if (ex != null) {
						log.error(ex.getMessage(), ex);
						return EXPLAIN_URL;
					} else {
						return result;
					}
				}).thenAccept(callback::accept);
	}
}
