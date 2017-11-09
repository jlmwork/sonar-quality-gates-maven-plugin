package nl.slam_it.maven.plugin;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import nl.slam_it.maven.plugin.model.Error;
import nl.slam_it.maven.plugin.model.Event;
import nl.slam_it.maven.plugin.model.ProjectStatus;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mashape.unirest.http.Unirest.setHttpClient;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.apache.http.impl.client.HttpClients.createDefault;

@Mojo(name = "inspect")
public class SonarQualityGatesMojo extends AbstractMojo {

    private static final String COLON = ":";
    private static final String SONAR_API_URL = "%s/api/qualitygates/project_status?projectKey=%s";
    private static final String SONAR_DEFAULT_HOST_URL = "http://localhost:9000";
    private static final String SONAR_HOST_URL = "sonar.host.url";
    private static final String SONAR_PROJECT_KEY = "sonar.projectKey";
    private static final int FIRST = 0;
    private static final int STATUS_CODE_OK = 200;

    private final SonarObjectMapper sonarObjectMapper;

    @Component
    private MojoExecution execution;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Parameter(property = SONAR_HOST_URL)
    private String sonarHostUrl;

    /**
     * Set this to 'true' to skip analysis.
     *
     * @since 2.0
     */
    @Parameter(alias = "sonar.gate.skip", property = "sonar.gate.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Wait until reaching the last project before executing checker when attached to phase
     */
    static final AtomicInteger readyProjectsCounter = new AtomicInteger();

    @Inject
    public SonarQualityGatesMojo(SonarObjectMapper sonarObjectMapper) {
        setHttpClient(createDefault());
        this.sonarObjectMapper = sonarObjectMapper;
    }

    public void execute() throws MojoExecutionException, MojoFailureException {
        if(skip) {
          return ;
        }
        if (shouldDelayExecution()) {
          getLog().info("Delaying SonarQubeQualityGate Checker to the end of multi-module project");
          return;
        }

        MavenProject topLevelProject = session.getTopLevelProject();
        ProjectStatus projectStatus = retrieveSonarprojectStatus(format(SONAR_API_URL, getSonarHostUrl(topLevelProject.getProperties()), getSonarKey(topLevelProject)));
        if(!projectStatus.getStatus().equals("OK")) {
          String errorMsg = "Quality Gate has failed : " + projectStatus.getErrorsMessage();
          getLog().error(errorMsg);
          throw new MojoFailureException(errorMsg);
        }
        getLog().info("Quality Gate successfully passed.");
    }

    private ProjectStatus retrieveSonarprojectStatus(String url) throws MojoFailureException {
        try {
            HttpResponse<String> response = Unirest.get(url).asString();
            String body = response.getBody();

            if (response.getStatus() != STATUS_CODE_OK) {
                String errorMessage = sonarObjectMapper.readValue(body, Error.class).getMessage();
                throw new MojoFailureException("Sonar responded with an error message: " + errorMessage);
            }

            return sonarObjectMapper.readValue(body, ProjectStatus.class);
        } catch (UnirestException e) {
            throw new MojoFailureException("Could not execute sonar-quality-gates-plugin", e);
        } finally {
            shutdown();
        }
    }

    private String getSonarHostUrl(Properties properties) {
        if (sonarHostUrl != null) {
            return sonarHostUrl;
        }

        return properties.containsKey(SONAR_HOST_URL) ? properties.getProperty(SONAR_HOST_URL) : SONAR_DEFAULT_HOST_URL;
    }

    private String getSonarKey(MavenProject pom) {
        if (pom.getModel().getProperties().containsKey(SONAR_PROJECT_KEY)) {
            return pom.getModel().getProperties().getProperty(SONAR_PROJECT_KEY);
        }

        return pom.getGroupId() + COLON + pom.getArtifactId();
    }

    private void shutdown() throws MojoFailureException {
        try {
            Unirest.shutdown();
        } catch (IOException e) {
            throw new MojoFailureException("Could not properly shutdown", e);
        }
    }


    /**
     * Should checker be delayed?
     * @return true if goal is attached to phase and not last in a multi-module project
     */
    private boolean shouldDelayExecution() {
      return !isDetachedGoal() && !isLastProjectInReactor();
    }

    /**
     * Is this execution a 'detached' goal run from the cli.  e.g. mvn sonar:sonar
     *
     * See <a href="https://maven.apache.org/guides/mini/guide-default-execution-ids.html#Default_executionIds_for_Implied_Executions">
        Default executionIds for Implied Executions</a>
    * for explanation of command line execution id.
    *
    * @return true if this execution is from the command line
    */
    private boolean isDetachedGoal() {
      return "default-cli".equals(execution.getExecutionId());
    }

    /**
     * Is this project the last project in the reactor?
     *
     * See <a href="http://svn.apache.org/viewvc/maven/plugins/tags/maven-install-plugin-2.5.2/src/main/java/org/apache/maven/plugin/install/InstallMojo.java?view=markup">
        install plugin</a> for another example of using this technique.
    *
    * @return true if last project (including only project)
    */
    private boolean isLastProjectInReactor() {
      boolean isLast = (readyProjectsCounter.incrementAndGet() >= session.getProjects().size());
      getLog().info("Projects in the reactor : " + session.getProjects().size());
      getLog().info("Current number of projects analyzed : " + readyProjectsCounter);
      return isLast;
    }
}
