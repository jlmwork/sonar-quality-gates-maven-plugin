package nl.slam_it.maven.plugin;

import com.mashape.unirest.http.Unirest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.MojoRule;
import org.apache.maven.plugin.testing.resources.TestResources;
import org.apache.maven.project.MavenProject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class SonarQualityGatesMojoTest {

    @Rule
    public MojoRule rule = new MojoRule();
    @Rule
    public TestResources resources = new TestResources();
    @Rule
    public ExpectedException exception = ExpectedException.none();
    private HttpServer server;
    private SonarEventHandler sonarEventHandler;
    private Mojo mojo;
    private MavenProject project;

    @Before
    public void setUp() throws Exception {
        sonarEventHandler = new SonarEventHandler();

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", sonarEventHandler);
        server.setExecutor(null);
        server.start();

        project = rule.readMavenProject(resources.getBasedir(""));
        mojo = rule.lookupConfiguredMojo(project, "inspect");

        project.getProperties().put("sonar.host.url", "http://localhost:" + server.getAddress().getPort());
    }

    @After
    public void tearDown() {
        server.stop(0);
    }

    @Test
    public void redEvent() throws Exception {
        exception.expect(MojoFailureException.class);
        exception.expectMessage(containsString("new_coverage"));
        exception.expectMessage(containsString("new_blocker_violations"));
        exception.expectMessage(containsString("new_critical_violations"));
        // "Critical issues != 0, Coverage < 75");

        try {
            sonarEventHandler.setResponse(200, getResponse("red.json"));
            mojo.execute();
        } finally {
            assertThat(sonarEventHandler.getResource(), is("nl.slam-it.maven:test-sonar-quality-gates-maven-plugin"));
        }
    }

    @Test
    public void passedGate() throws MojoFailureException, MojoExecutionException {
        sonarEventHandler.setResponse(200, getResponse("passed.json"));

        mojo.execute();

        assertThat(sonarEventHandler.getResource(), is("nl.slam-it.maven:test-sonar-quality-gates-maven-plugin"));
    }

    @Test
    public void sonarProjectKeyProperty() throws MojoFailureException, MojoExecutionException {
        sonarEventHandler.setResponse(200, getResponse("passed.json"));
        project.getProperties().put("sonar.projectKey", "nl.slam-it.maven:test-property");

        mojo.execute();

        assertThat(sonarEventHandler.getResource(), is("nl.slam-it.maven:test-property"));
    }

    @Test
    public void error() throws Exception {
        exception.expect(MojoFailureException.class);
        exception.expectMessage(startsWith("Sonar responded with an error message:"));

        try {
            sonarEventHandler.setResponse(404, getResponse("error.json"));
            mojo.execute();
        } finally {
            assertThat(sonarEventHandler.getResource(), is("nl.slam-it.maven:test-sonar-quality-gates-maven-plugin"));
        }
    }

    @Test
    public void unirestException() throws MojoFailureException, MojoExecutionException {
        exception.expect(MojoFailureException.class);
        // exception.expectMessage("Could not execute sonar-quality-gates-plugin");

        Unirest.setHttpClient(null);

        mojo.execute();
    }

    private String getResponse(String file) {
        return new Scanner(getClass().getClassLoader().getResourceAsStream(file)).useDelimiter("\\Z").next();
    }

    private static final class SonarEventHandler implements HttpHandler {
        private static final Pattern RESOURCE_PATTERN = Pattern.compile("^.*projectKey=(.*)$");

        private String response = "[]";
        private String resource = "";
        private int status = 200;

        public void handle(final HttpExchange httpExchange) throws IOException {
            resource = getResource(httpExchange.getRequestURI().getQuery());

            try (OutputStream responseBody = httpExchange.getResponseBody()) {
                httpExchange.sendResponseHeaders(status, response.length());
                responseBody.write(response.getBytes());
            }
        }

        public void setResponse(final int status, final String response) {
            this.status = status;
            this.response = response;
        }

        public String getResource() {
            return resource;
        }

        private String getResource(final String query) {
            Matcher matcher = RESOURCE_PATTERN.matcher(query);

            if (matcher.matches()) {
                return matcher.group(1);
            }

            return "";
        }
    }
}
