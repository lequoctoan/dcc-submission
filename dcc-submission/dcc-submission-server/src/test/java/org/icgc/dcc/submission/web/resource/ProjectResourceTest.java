package org.icgc.dcc.submission.web.resource;

import static javax.ws.rs.client.Entity.json;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.glassfish.grizzly.http.util.Header.Authorization;
import static org.glassfish.jersey.internal.util.Base64.encodeAsString;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Set;

import lombok.val;

import org.icgc.dcc.submission.core.AbstractDccModule;
import org.icgc.dcc.submission.core.model.Project;
import org.icgc.dcc.submission.fs.DccFileSystem;
import org.icgc.dcc.submission.release.model.Release;
import org.icgc.dcc.submission.release.model.Submission;
import org.icgc.dcc.submission.service.ProjectService;
import org.icgc.dcc.submission.service.ReleaseService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.inject.Module;
import com.mongodb.CommandResult;
import com.mongodb.MongoException.DuplicateKey;

public class ProjectResourceTest extends ResourceTest {

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private final String UNAUTH_USER = "nobody";
  private final String AUTH_ALLOWED_USER = "richard";
  private final String AUTH_NOT_ALLOWED_USER = "ricardo";

  private static final String AUTH_HEADER = Authorization.toString();

  private String getAuthValue(String username) {
    return "X-DCC-Auth " + encodeAsString(username + ":" + username + "spasswd");
  }

  private ProjectService projectService;

  private ReleaseService releaseService;

  private DccFileSystem dccFileSystem;

  private Project projectOne;

  private Project projectTwo;

  private Release release;

  private Set<Release> releases;

  private Submission submissionOne;

  private Submission submissionTwo;

  private Set<Submission> submissions;

  private final String PATH = "path/to/release/project";

  @Override
  protected Collection<? extends Module> configureModules() {
    return ImmutableList.of(new AbstractDccModule() {

      @Override
      protected void configure() {
        dccFileSystem = mock(DccFileSystem.class);
        when(dccFileSystem.mkdirProjectDirectory(any(String.class), any(String.class))).thenReturn(PATH);

        release = new Release("REL1");
        releases = Sets.newHashSet(release);

        releaseService = mock(ReleaseService.class);
        when(releaseService.findOpen()).thenReturn(release);
        when(releaseService.findAll()).thenReturn(releases);
        when(releaseService.addSubmission(any(String.class), any(String.class))).thenReturn(release);

        projectOne = new Project("PRJ1", "Project One");
        projectOne.setUsers(Sets.newHashSet(AUTH_ALLOWED_USER));
        projectTwo = new Project("PRJ2", "Project Two");

        submissionOne = new Submission("PRJ1", "Project One", release.getName());
        submissionTwo = new Submission("PRJ2", "Project Two", release.getName());
        submissions = Sets.newHashSet(submissionOne, submissionTwo);

        projectService = mock(ProjectService.class);
        when(projectService.find(projectOne.getKey())).thenReturn(projectOne);
        when(projectService.findForUser(projectOne.getKey(), AUTH_ALLOWED_USER)).thenReturn(projectOne);
        when(projectService.findAllForUser(any(String.class))).thenReturn(Sets.newHashSet(projectOne));
        when(projectService.findAll()).thenReturn(Sets.newHashSet(projectOne, projectTwo));
        when(projectService.extractSubmissions(releases, projectOne.getKey())).thenReturn(submissions);

        bind(ProjectService.class).toInstance(projectService);
        bind(ReleaseService.class).toInstance(releaseService);
        bind(DccFileSystem.class).toInstance(dccFileSystem);
      }

    });
  }

  @Test
  public void testGetProjectsWhenNotAuthorized() {
    val reponse =
        target().path("projects").request(MIME_TYPE).header(AUTH_HEADER, getAuthValue(UNAUTH_USER)).get();
    verifyZeroInteractions(projectService);
    assertThat(reponse.getStatus()).isEqualTo(UNAUTHORIZED.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("");
  }

  @Test
  public void testGetProjectsWhenAuthorized() {
    val reponse =
        target().path("projects").request(MIME_TYPE).header(AUTH_HEADER, getAuthValue(AUTH_ALLOWED_USER)).get();
    verify(projectService).findAllForUser(any(String.class));
    assertThat(reponse.getStatus()).isEqualTo(OK.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("[{\"key\":\"PRJ1\",\"name\":\"Project One\"}]");
  }

  @Test
  public void testGetProjectsWhenAuthorizedAsAdmin() {
    val reponse = target().path("projects").request(MIME_TYPE).get();
    verify(projectService, atLeast(1)).findAll();
    assertThat(reponse.getStatus()).isEqualTo(OK.getStatusCode());
    assertThat(reponse.readEntity(String.class))
        .isEqualTo("[{\"key\":\"PRJ2\",\"name\":\"Project Two\"},{\"key\":\"PRJ1\",\"name\":\"Project One\"}]");
  }

  @Test
  public void testGetProjectWhenNotAuthorized() {
    val reponse =
        target().path("projects/" + projectOne.getKey()).request(MIME_TYPE)
            .header(AUTH_HEADER, getAuthValue(UNAUTH_USER)).get();
    verifyZeroInteractions(projectService);
    assertThat(reponse.getStatus()).isEqualTo(UNAUTHORIZED.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("");
  }

  @Test
  public void testGetProjectWhenAuthorizedWithoutAccess() {
    val reponse =
        target().path("projects/" + projectOne.getKey()).request(MIME_TYPE)
            .header(AUTH_HEADER, getAuthValue(AUTH_NOT_ALLOWED_USER))
            .get();
    verify(projectService, never()).find(any(String.class));
    assertThat(reponse.getStatus()).isEqualTo(NOT_FOUND.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("{\"code\":\"NoSuchEntity\",\"parameters\":[\"PRJ1\"]}");
  }

  @Test
  public void testGetProjectWhenAuthorizedWithAccess() {
    val reponse =
        target().path("projects/" + projectOne.getKey()).request(MIME_TYPE)
            .header(AUTH_HEADER, getAuthValue(AUTH_ALLOWED_USER)).get();
    verify(projectService).find(projectOne.getKey());
    assertThat(reponse.getStatus()).isEqualTo(OK.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("{\"key\":\"PRJ1\",\"name\":\"Project One\"}");
  }

  @Test
  public void testGetProjectWhenAuthorizedDoesNotExist() {
    val reponse =
        target().path("projects/DNE").request(MIME_TYPE).get();
    assertThat(reponse.getStatus()).isEqualTo(NOT_FOUND.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("{\"code\":\"NoSuchEntity\",\"parameters\":[\"DNE\"]}");
  }

  @Test
  public void testAddProjectWhenNotAuthorized() throws Exception {
    val reponse =
        target().path("projects").request(MIME_TYPE).header(AUTH_HEADER, getAuthValue(UNAUTH_USER)).post(json("{}"));
    verifyZeroInteractions(projectService);
    assertThat(reponse.getStatus()).isEqualTo(UNAUTHORIZED.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("");
  }

  @Test
  public void testAddProjectWithMissingFields() throws Exception {
    val projectJson = json("{\"keymissing\":\"PRJ1\",\"name\":\"Project One\"}");
    val reponse = target().path("projects").request(MIME_TYPE).post(projectJson);
    verifyZeroInteractions(projectService);
    // Apparently JAX-RS doesn't have a '422 Unprocessable Entity' Status
    assertThat(reponse.getStatus()).isEqualTo(422);
  }

  @Test
  public void testAddProjectWithInvalidSymbolsInProjectKey() throws Exception {
    val projectJson = json("{\"key\":\"P#$%^&*RJ1\",\"name\":\"Project One\"}");
    val reponse = target().path("projects").request(MIME_TYPE).post(projectJson);
    verifyZeroInteractions(projectService);
    // Apparently JAX-RS doesn't have a '422 Unprocessable Entity' Status
    assertThat(reponse.getStatus()).isEqualTo(422);
  }

  @Test
  public void testAddProjectWithSpaceInProjectKey() throws Exception {
    val projectJson = json("{\"key\":\"P RJ1\",\"name\":\"Project One\"}");
    val reponse = target().path("projects").request(MIME_TYPE).post(projectJson);
    verifyZeroInteractions(projectService);
    // Apparently JAX-RS doesn't have a '422 Unprocessable Entity' Status
    assertThat(reponse.getStatus()).isEqualTo(422);
  }

  @Test
  public void testAddProjectWhenNotAdmin() throws Exception {
    val projectJson = json("{\"key\":\"PRJ1\",\"name\":\"Project One\"}");
    val reponse =
        target().path("projects").request(MIME_TYPE).header(AUTH_HEADER, getAuthValue(AUTH_ALLOWED_USER))
            .post(projectJson);

    verify(projectService, never()).add(any(Project.class));

    assertThat(reponse.getStatus()).isEqualTo(UNAUTHORIZED.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("{\"code\":\"Unauthorized\",\"parameters\":[]}");
  }

  @Test
  public void testAddProjectWhenAdmin() throws Exception {
    val projectJson = json("{\"key\":\"PRJ1\",\"name\":\"Project One\"}");
    val reponse = target().path("projects").request(MIME_TYPE).post(projectJson);

    verify(projectService).add(any(Project.class));
    verify(releaseService).addSubmission("PRJ1", "Project One");
    verify(dccFileSystem).mkdirProjectDirectory("REL1", "PRJ1");
    assertThat(dccFileSystem.mkdirProjectDirectory("REL1", "PRJ1")).isEqualTo(PATH);

    assertThat(reponse.getStatus()).isEqualTo(CREATED.getStatusCode());
    assertThat(reponse.getLocation().toString()).isEqualTo("projects/PRJ1");
  }

  @Test
  public void testAddProjectThatAlreadyExists() throws Exception {
    doThrow(new DuplicateKey(mock(CommandResult.class))).when(projectService).add(any(Project.class));

    val projectJson = json("{\"key\":\"PRJ1\",\"name\":\"Project One\"}");
    val reponse = target().path("projects").request(MIME_TYPE).post(projectJson);

    verify(projectService).add(any(Project.class));
    verifyZeroInteractions(releaseService);

    assertThat(reponse.getStatus()).isEqualTo(BAD_REQUEST.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("{\"code\":\"AlreadyExists\",\"parameters\":[\"PRJ1\"]}");
  }

  @Test
  public void testUpdateProjectWhenNotAdmin() throws Exception {
    val projectJson = json("{\"key\":\"PRJ1\",\"name\":\"Project One\"}");
    val reponse =
        target().path("projects/PRJ1").request(MIME_TYPE).header(AUTH_HEADER, getAuthValue(AUTH_ALLOWED_USER))
            .post(projectJson);

    verify(projectService, never()).update(any(Project.class));

    assertThat(reponse.getStatus()).isEqualTo(UNAUTHORIZED.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("{\"code\":\"Unauthorized\",\"parameters\":[]}");
  }

  @Test
  public void testUpdateProjectWhenAdmin() throws Exception {
    val projectJson = json("{\"key\":\"PRJ1\",\"name\":\"Project One\"}");
    val reponse = target().path("projects/PRJ1").request(MIME_TYPE).post(projectJson);

    verify(projectService).update(any(Project.class));

    assertThat(reponse.getStatus()).isEqualTo(OK.getStatusCode());
  }

  @Test
  public void testUpdateProjectWrongKey() throws Exception {
    val projectJson = json("{\"key\":\"PRJ1\",\"name\":\"Project One\"}");
    val reponse = target().path("projects/PRJ2").request(MIME_TYPE).post(projectJson);

    verify(projectService, never()).update(any(Project.class));

    assertThat(reponse.getStatus()).isEqualTo(PRECONDITION_FAILED.getStatusCode());
  }

  @Test
  public void testGetProjectSubmissionsWhenNotAuthorized() throws Exception {
    val reponse =
        target().path("projects/" + projectOne.getKey() + "/releases").request(MIME_TYPE)
            .header(AUTH_HEADER, getAuthValue(UNAUTH_USER)).get();

    verifyZeroInteractions(releaseService);
    verifyZeroInteractions(projectService);

    assertThat(reponse.getStatus()).isEqualTo(UNAUTHORIZED.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("");
  }

  @Test
  public void testGetProjectSubmissionsWhenAuthorizedWithoutAccess() {
    val reponse =
        target().path("projects/" + projectOne.getKey()).request(MIME_TYPE)
            .header(AUTH_HEADER, getAuthValue(AUTH_NOT_ALLOWED_USER)).get();

    verifyZeroInteractions(releaseService);
    verify(projectService, never()).find(any(String.class));

    assertThat(reponse.getStatus()).isEqualTo(NOT_FOUND.getStatusCode());
    assertThat(reponse.readEntity(String.class)).isEqualTo("{\"code\":\"NoSuchEntity\",\"parameters\":[\"PRJ1\"]}");
  }

  @Test
  public void testGetProjectSubmissions() throws Exception {
    val reponse = target().path("projects/" + projectOne.getKey() + "/releases").request(MIME_TYPE).get();

    verify(releaseService).findAll();
    verify(projectService).extractSubmissions(releases, projectOne.getKey());

    assertThat(reponse.getStatus()).isEqualTo(OK.getStatusCode());
    assertThat(reponse.readEntity(String.class))
        .isEqualTo(
            "[{\"projectKey\":\"PRJ2\",\"projectName\":\"Project Two\",\"releaseName\":\"REL1\",\"lastUpdated\":"
                + submissionTwo.getLastUpdated().getTime() + ",\"state\":\"NOT_VALIDATED\",\"report\":null},"
                + "{\"projectKey\":\"PRJ1\",\"projectName\":\"Project One\",\"releaseName\":\"REL1\",\"lastUpdated\":"
                + submissionOne.getLastUpdated().getTime() + ",\"state\":\"NOT_VALIDATED\",\"report\":null}]");
  }
}