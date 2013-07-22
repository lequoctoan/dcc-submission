/*
 * Copyright (c) 2013 The Ontario Institute for Cancer Research. All rights reserved.                             
 *                                                                                                               
 * This program and the accompanying materials are made available under the terms of the GNU Public License v3.0.
 * You should have received a copy of the GNU General Public License along with                                  
 * this program. If not, see <http://www.gnu.org/licenses/>.                                                     
 *                                                                                                               
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY                           
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES                          
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT                           
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,                                
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED                          
 * TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;                               
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER                              
 * IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN                         
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.icgc.dcc.submission.sftp;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.lang.String.format;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.icgc.dcc.submission.fs.DccFileSystem.VALIDATION_DIRNAME;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.shiro.subject.Subject;
import org.apache.sshd.SshServer;
import org.icgc.dcc.submission.core.ProjectService;
import org.icgc.dcc.submission.core.ProjectServiceException;
import org.icgc.dcc.submission.core.model.Project;
import org.icgc.dcc.submission.core.model.Status;
import org.icgc.dcc.submission.core.model.UserSession;
import org.icgc.dcc.submission.fs.DccFileSystem;
import org.icgc.dcc.submission.fs.ReleaseFileSystem;
import org.icgc.dcc.submission.fs.SubmissionDirectory;
import org.icgc.dcc.submission.release.NextRelease;
import org.icgc.dcc.submission.release.ReleaseService;
import org.icgc.dcc.submission.release.model.Release;
import org.icgc.dcc.submission.release.model.Submission;
import org.icgc.dcc.submission.security.UsernamePasswordAuthenticator;
import org.icgc.dcc.submission.sftp.SftpAuthenticator;
import org.icgc.dcc.submission.sftp.SftpContext;
import org.icgc.dcc.submission.sftp.SftpServerService;
import org.icgc.dcc.submission.sftp.SshServerProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.eventbus.EventBus;
import com.google.common.io.CharStreams;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.typesafe.config.Config;

@RunWith(MockitoJUnitRunner.class)
@Slf4j
public class SftpServerServiceTest {

  /**
   * Test configuration.
   */
  private static final String RELEASE_NAME = "release1";
  private static final String PROJECT_KEY = "project1";
  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";
  private static final int NIO_WORKERS = 3;
  private static final String VALIDATION_FILE_NAME = "dummy-validation-file.txt";

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();
  @Rule
  public Sftp sftp = new Sftp(USERNAME, PASSWORD, false);

  @Mock
  Config config;
  @Mock
  Subject subject;
  @Mock
  UsernamePasswordAuthenticator authenticator;

  @Mock
  Release release;
  @Mock
  Submission submission;
  @Mock
  Project project;
  @Mock
  NextRelease nextRelease;

  @Mock
  DccFileSystem fs;
  @Mock
  SubmissionDirectory submissionDirectory;
  @Mock
  ReleaseFileSystem releaseFileSystem;

  @Mock
  ProjectService projectService;
  @Mock
  ReleaseService releaseService;

  SftpServerService service;
  File root;

  @Before
  public void setUp() throws IOException, JSchException {
    // Create root of file system
    root = tmp.newFolder(RELEASE_NAME);

    // Mock configuration
    when(config.getInt("sftp.port")).thenReturn(sftp.getPort());
    when(config.getString("sftp.path")).thenReturn("/tmp/file.pem");
    when(config.hasPath("sftp.nio-workers")).thenReturn(true);
    when(config.getInt("sftp.nio-workers")).thenReturn(NIO_WORKERS);

    // Mock authentication
    when(authenticator.authenticate(anyString(), (char[]) any(), anyString())).thenReturn(subject);
    when(authenticator.getSubject()).thenReturn(subject);

    // Mock release / project
    when(project.getKey()).thenReturn(PROJECT_KEY);
    when(release.getName()).thenReturn(RELEASE_NAME);
    when(nextRelease.getRelease()).thenReturn(release);
    when(releaseService.getNextRelease()).thenReturn(nextRelease);
    when(projectService.getProject(PROJECT_KEY)).thenReturn(project);
    when(projectService.getProject(not(eq(PROJECT_KEY)))).thenThrow(new ProjectServiceException(""));
    when(projectService.getProjectsBySubject(any(Subject.class))).thenReturn(newArrayList(project));

    // Mock file system
    when(fs.buildReleaseStringPath(release)).thenReturn(root.getAbsolutePath());
    when(fs.getReleaseFilesystem(release, subject)).thenReturn(releaseFileSystem);
    when(fs.getFileSystem()).thenReturn(fileSystem());
    when(releaseFileSystem.getDccFileSystem()).thenReturn(fs);
    when(releaseFileSystem.getRelease()).thenReturn(release);
    when(releaseFileSystem.getSubmissionDirectory(PROJECT_KEY)).thenReturn(submissionDirectory);
    when(submissionDirectory.isReadOnly()).thenReturn(false);
    when(submissionDirectory.getSubmission()).thenReturn(submission);

    // Create CUT
    service = createService();

    // Start CUT
    service.startAndWait();

    sftp.connect();
  }

  @After
  public void tearDown() {
    service.stop();
    sftp.disconnect();
  }

  @Test
  public void testTypicalWorkflow() throws JSchException, SftpException {
    // Create the simulated project directory
    String projectDirectoryName = createProjectDirectory();

    // Original file
    String filePath = filePath(1);
    String fileContent = "This is the content of the file";
    File file = new File(root, filePath);

    // New file
    String newFilePath = filePath(2);
    File newFile = new File(root, newFilePath);

    // Initial state
    assertThat(pwd()).isEqualTo("/");
    assertThat(ls(projectDirectoryName)).hasSize(0);

    // Change directory
    cd(projectDirectoryName);
    assertThat(pwd()).isEqualTo(projectDirectoryName);

    // Put file
    put(filePath, fileContent);
    assertThat(file).exists().hasContent(fileContent);
    assertThat(ls(projectDirectoryName)).hasSize(1);

    // Rename file
    rename(filePath, newFilePath);
    assertThat(file).doesNotExist();
    assertThat(newFile).exists().hasContent(fileContent);
    assertThat(ls(projectDirectoryName)).hasSize(1);

    // Remove file
    rm(newFilePath);
    assertThat(newFile).doesNotExist();
    assertThat(ls(projectDirectoryName)).isEmpty();
  }

  @Test(expected = IOException.class)
  public void testGetNotPossible() throws SftpException, IOException {
    // Create the simulated project directory
    String projectDirectoryName = createProjectDirectory();

    // File
    String filePath = filePath(1);
    String fileContent = "This is the content of the file";
    File file = new File(root, filePath);

    // Change directory
    cd(projectDirectoryName);
    assertThat(pwd()).isEqualTo(projectDirectoryName);

    // Put file
    put(filePath, fileContent);
    assertThat(file).exists().hasContent(fileContent);

    // This should throw
    get(filePath);
  }

  /**
   * DCC-1082
   */
  @Test(expected = SftpException.class)
  public void testRemoveValidationFileNotPossible() throws SftpException {
    // Create the simulated project directory
    String projectDirectoryName = createProjectDirectory();

    rm(projectDirectoryName + "/" + VALIDATION_DIRNAME + "/" + VALIDATION_FILE_NAME);
  }

  /**
   * DCC-1071
   */
  @Test
  public void testPutToDotCurrentDirectory() throws SftpException, IOException {
    // Create the simulated project directory
    String projectDirectoryName = createProjectDirectory();

    // Source file
    String filePath = filePath(1);
    String fileContent = "This is the content of the file";
    File file = new File(root, filePath);
    file.createNewFile();
    String fileName = file.getName();

    // Destination directory
    String currentDirectoryName = ".";

    // Change directory
    cd(projectDirectoryName);
    assertThat(pwd()).isEqualTo(projectDirectoryName);

    // Put file
    put(file.getAbsolutePath(), currentDirectoryName, fileContent);
    assertThat(ls(projectDirectoryName)).hasSize(1);
    assertThat(ls(projectDirectoryName).get(0).getFilename()).isEqualTo(fileName);
  }

  /**
   * DCC-1071
   */
  @Test
  public void testCdToHomeDirectory() throws SftpException, IOException {
    // Create the simulated project directory
    createProjectDirectory();

    // Destination directory
    String homeDirectoryName = "~";
    String rootDirectoryName = "/";

    // Change directory
    cd(homeDirectoryName);
    assertThat(pwd()).isEqualTo(rootDirectoryName);
  }

  /**
   * DCC-1071
   */
  @Test
  public void testRemoveDotCurrentDirectoryStar() throws SftpException {
    // Create the simulated project directory
    String projectDirectoryName = createProjectDirectory();

    // File
    String filePath = filePath(1);
    String fileContent = "This is the content of the file";

    // Change directory
    cd(projectDirectoryName);
    assertThat(pwd()).isEqualTo(projectDirectoryName);

    // Put file
    put(filePath, fileContent);
    assertThat(ls(projectDirectoryName)).hasSize(1);

    // Remove all files in the current directory
    rm("./*");
    assertThat(ls(projectDirectoryName)).isEmpty();
  }

  @Test(expected = SftpException.class)
  public void testRemoveNonExistentFile() throws SftpException {
    rm("/does/not/exist");
  }

  @Test(expected = SftpException.class)
  public void testCdIntoNonExistent() throws SftpException {
    cd("/does/not/exist");
  }

  @Test(expected = SftpException.class)
  public void testLsIntoNonExistent() throws SftpException {
    ls("/does/not/exist");
  }

  @Test(expected = SftpException.class)
  public void testRenameNonExistent() throws SftpException {
    rename("/does/not/exist", "/still/does/not/exist");
  }

  @Test(expected = SftpException.class)
  public void testGetNonExistent() throws SftpException, IOException {
    get("/does/not/exist");
  }

  @Test
  public void testActiveSession() throws InterruptedException {
    // Connected
    checkActiveSessions(1);

    // Disconnect
    disconnectAndCheck();
  }

  // Re-enable in DCC-1226
  @Ignore
  @Test
  public void testMaxSession() {
    int extraClientCount = NIO_WORKERS + 1; // one is already connected

    ExecutorService executor = newFixedThreadPool(extraClientCount);
    for (int i = 0; i < extraClientCount; i++) {
      final int thread = i + 1;
      executor.execute(new Runnable() {

        @Override
        public void run() {
          Sftp sftpTmp = new Sftp(USERNAME, PASSWORD, false);
          try {
            log.info("Connecting - {}", thread);
            sftpTmp.connect();
            log.info("Connected - {}", thread);

            String projectDirectoryName = createProjectDirectory(); // Somehow it seems we can't write at the root...
            log.info(lsString(sftpTmp, projectDirectoryName));

            log.info("Writting - {}", thread);
            sftpTmp.getChannel().put(new ByteArrayInputStream(new byte[250000000]), filePath("dummy" + thread));

            log.info("Written - {}", thread);
            log.info(lsString(sftpTmp, projectDirectoryName));

            log.info("Disconnecting - {}", thread);
            sftpTmp.disconnect();
            log.info("Disconnected - {}", thread);

          } catch (Exception e) {
            e.printStackTrace();
            fail();
          }
        }

        @SuppressWarnings("unchecked")
        private String lsString(Sftp sftpTmp, String projectDirectoryName) throws SftpException {
          return new ArrayList<String>(sftpTmp.getChannel().ls(projectDirectoryName)).toString();
        }

      });

      sleepUninterruptibly(1000, MILLISECONDS);
    }

    sleepUninterruptibly(extraClientCount * 1500, MILLISECONDS);
    checkActiveSessions(extraClientCount + 1); // One is already connected

    sleepUninterruptibly(extraClientCount * 15000, MILLISECONDS);
    disconnectAndCheck();
  }

  private SftpServerService createService() {
    SftpContext context = new SftpContext(fs, releaseService, projectService, authenticator);
    SftpAuthenticator authenticator = new SftpAuthenticator(context);
    SshServer sshd = new SshServerProvider(config, context, authenticator).get();
    EventBus eventBus = new EventBus();
    eventBus.register(authenticator);

    return new SftpServerService(sshd, eventBus);
  }

  private void disconnectAndCheck() {
    sftp.disconnect();
    sleepUninterruptibly(1, SECONDS);
    checkActiveSessions(0);
  }

  private void checkActiveSessions(int total) {
    Status activeSessions = service.getActiveSessions();
    assertThat(activeSessions.getActiveSftpSessions()).isEqualTo(total);

    List<UserSession> userSessions = activeSessions.getUserSessions();
    assertThat(userSessions.size()).isEqualTo(total);

    for (int i = 0; i < total; i++) {
      val userSession = userSessions.get(i);
      assertThat(userSession.getUserName()).isEqualTo(USERNAME);
    }
  }

  private static RawLocalFileSystem fileSystem() {
    RawLocalFileSystem localFileSystem = new RawLocalFileSystem();
    localFileSystem.setConf(new Configuration());

    return localFileSystem;
  }

  @SneakyThrows
  private String createProjectDirectory() {
    // Create base directory
    String projectDirectoryName = getProjectDirectoryName();
    File projectDirectory = new File(root, projectDirectoryName);
    projectDirectory.mkdir();

    // Create validation directory
    String validationDirectoryName = VALIDATION_DIRNAME;
    File validationDirectory = new File(projectDirectory, validationDirectoryName);
    validationDirectory.mkdir();

    // Create validation file
    String validationFileName = VALIDATION_FILE_NAME;
    File validationFile = new File(validationDirectory, validationFileName);
    validationFile.createNewFile();

    return projectDirectoryName;
  }

  private String getProjectDirectoryName() {
    return "/" + PROJECT_KEY;
  }

  @SuppressWarnings("unchecked")
  private List<LsEntry> ls(String projectDirectoryName) throws SftpException {
    return sftp.getChannel().ls(projectDirectoryName);
  }

  private String pwd() throws SftpException {
    return sftp.getChannel().pwd();
  }

  private void rm(String newFileName) throws SftpException {
    sftp.getChannel().rm(newFileName);
  }

  private void rename(String fileName, String newFileName) throws SftpException {
    sftp.getChannel().rename(fileName, newFileName);
  }

  private void put(String sourceFileName, String fileContent) throws SftpException {
    sftp.getChannel().put(inputStream(fileContent), sourceFileName);
  }

  private void put(String sourceFileName, String destFileName, String fileContent) throws SftpException {
    sftp.getChannel().put(sourceFileName, destFileName);
  }

  private String get(String fileName) throws SftpException, IOException {
    return read(sftp.getChannel().get(fileName));
  }

  private void cd(String projectDirectoryName) throws SftpException {
    sftp.getChannel().cd(projectDirectoryName);
  }

  private static String filePath(int i) {
    return filePath(String.valueOf(i));
  }

  private static String filePath(String s) {
    return format("/%s/file%s.txt", PROJECT_KEY, s);
  }

  private static String read(InputStream inputStream) throws IOException {
    return CharStreams.toString(new InputStreamReader(inputStream, UTF_8));
  }

  private static InputStream inputStream(String text) {
    return new ByteArrayInputStream(text.getBytes(UTF_8));
  }

}