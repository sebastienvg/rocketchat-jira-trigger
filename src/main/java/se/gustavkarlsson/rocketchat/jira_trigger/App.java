package se.gustavkarlsson.rocketchat.jira_trigger;

import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.IssueRestClient;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.auth.AnonymousAuthenticationHandler;
import com.atlassian.jira.rest.client.auth.BasicHttpAuthenticationHandler;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.moandjiezana.toml.Toml;
import org.slf4j.Logger;
import se.gustavkarlsson.rocketchat.jira_trigger.configuration.Configuration;
import se.gustavkarlsson.rocketchat.jira_trigger.configuration.JiraConfiguration;
import se.gustavkarlsson.rocketchat.jira_trigger.converters.AttachmentConverter;
import se.gustavkarlsson.rocketchat.jira_trigger.converters.MessageCreator;
import se.gustavkarlsson.rocketchat.jira_trigger.models.Attachment;
import se.gustavkarlsson.rocketchat.jira_trigger.models.IncomingMessage;
import se.gustavkarlsson.rocketchat.jira_trigger.routes.DetectIssueRoute;
import spark.Request;
import spark.Spark;

import java.io.File;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.slf4j.LoggerFactory.getLogger;

public class App {
	public static final String APPLICATION_JSON = "application/json";
	private static final Logger log = getLogger(App.class);
	private static final String DEFAULTS_FILE_NAME = "defaults.toml";

	public static void main(String[] args) {
		if (args.length < 1) {
			log.error("No configuration file specified");
			System.exit(1);
		}
		try {
			Toml toml = parseToml(new File(args[0]));
			Configuration config = new Configuration(toml);
			setupServer(config);
		} catch (Exception e) {
			log.error("Fatal error", e);
			System.exit(1);
		}
	}

	private static Toml parseToml(File configFile) {
		return new Toml(parseDefaults()).read(configFile);
	}

	private static Toml parseDefaults() {
		return new Toml().read(Configuration.class.getClassLoader().getResourceAsStream(DEFAULTS_FILE_NAME));
	}

	private static void setupServer(Configuration config) {
		IssueRestClient issueClient = createIssueRestClient(config.getJiraConfiguration());
		Spark.port(config.getAppConfiguration().getPort());
		Spark.before((request, response) -> log(request));
		Supplier<IncomingMessage> messageCreator = new MessageCreator(config.getMessageConfiguration());
		BiFunction<Issue, Boolean, Attachment> attachmentConverter = new AttachmentConverter(config.getMessageConfiguration());
		Spark.post("/", APPLICATION_JSON, new DetectIssueRoute(config.getRocketChatConfiguration(), issueClient, messageCreator, attachmentConverter));
		Spark.exception(Exception.class, new UuidGeneratingExceptionHandler());
	}

	private static IssueRestClient createIssueRestClient(JiraConfiguration jiraConfig) {
		AuthenticationHandler authHandler = getAuthHandler(jiraConfig);
		AsynchronousJiraRestClientFactory factory = new AsynchronousJiraRestClientFactory();
		JiraRestClient jiraClient = factory.create(jiraConfig.getUri(), authHandler);
		return jiraClient.getIssueClient();
	}

	private static AuthenticationHandler getAuthHandler(JiraConfiguration jiraConfig) {
		String username = jiraConfig.getUsername();
		String password = jiraConfig.getPassword();
		if (username != null && password != null) {
			log.info("Using basic authentication");
			return new BasicHttpAuthenticationHandler(username, password);
		} else {
			log.info("No credentials configured. Using anonymous authentication");
			return new AnonymousAuthenticationHandler();
		}
	}

	private static void log(Request request) {
		log.info("Incoming request | IP: {} | Method: {} | Path: {} | Content-Length: {}",
				request.raw().getRemoteAddr(), request.requestMethod(), request.pathInfo(), request.contentLength());
	}

}
