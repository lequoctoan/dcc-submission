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
package org.icgc.dcc.submission.release;

import static com.google.common.base.Joiner.on;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.copyOf;
import static com.google.common.collect.Lists.newArrayList;
import static java.lang.String.format;
import static org.icgc.dcc.submission.core.MailUtils.email;
import static org.icgc.dcc.submission.release.model.SubmissionState.NOT_VALIDATED;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.apache.hadoop.fs.Path;
import org.apache.shiro.subject.Subject;
import org.icgc.dcc.submission.core.MailUtils;
import org.icgc.dcc.submission.core.model.BaseEntity;
import org.icgc.dcc.submission.core.model.DccConcurrencyException;
import org.icgc.dcc.submission.core.model.DccModelOptimisticLockException;
import org.icgc.dcc.submission.core.model.InvalidStateException;
import org.icgc.dcc.submission.core.model.Project;
import org.icgc.dcc.submission.core.model.QProject;
import org.icgc.dcc.submission.core.morphia.BaseMorphiaService;
import org.icgc.dcc.submission.dictionary.model.Dictionary;
import org.icgc.dcc.submission.dictionary.model.QDictionary;
import org.icgc.dcc.submission.fs.DccFileSystem;
import org.icgc.dcc.submission.fs.ReleaseFileSystem;
import org.icgc.dcc.submission.fs.SubmissionDirectory;
import org.icgc.dcc.submission.fs.SubmissionFile;
import org.icgc.dcc.submission.fs.hdfs.HadoopUtils;
import org.icgc.dcc.submission.release.model.DetailedSubmission;
import org.icgc.dcc.submission.release.model.LiteProject;
import org.icgc.dcc.submission.release.model.QRelease;
import org.icgc.dcc.submission.release.model.QueuedProject;
import org.icgc.dcc.submission.release.model.Release;
import org.icgc.dcc.submission.release.model.ReleaseState;
import org.icgc.dcc.submission.release.model.ReleaseView;
import org.icgc.dcc.submission.release.model.Submission;
import org.icgc.dcc.submission.release.model.SubmissionState;
import org.icgc.dcc.submission.shiro.AuthorizationPrivileges;
import org.icgc.dcc.submission.validation.report.SubmissionReport;
import org.icgc.dcc.submission.web.ServerErrorCode;
import org.icgc.dcc.submission.web.validator.InvalidNameException;
import org.icgc.dcc.submission.web.validator.NameValidator;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.google.code.morphia.query.UpdateOperations;
import com.google.code.morphia.query.UpdateResults;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.inject.Inject;
import com.mysema.query.mongodb.MongodbQuery;
import com.mysema.query.mongodb.morphia.MorphiaQuery;
import com.mysema.query.types.Predicate;
import com.typesafe.config.Config;

@Slf4j
public class ReleaseService extends BaseMorphiaService<Release> {

  /**
   * Ignoring releases whose name is starting with a specific prefix (see https://jira.oicr.on.ca/browse/DCC-1409 for
   * more details).
   */
  private static final String SPECIAL_RELEASE_PREFIX = "r--";

  private final DccLocking dccLocking;

  private final DccFileSystem fs;

  private final Config config;

  @Inject
  public ReleaseService(DccLocking dccLocking, Morphia morphia, Datastore datastore, DccFileSystem fs, Config config) {
    super(morphia, datastore, QRelease.release);
    checkArgument(dccLocking != null);
    checkArgument(fs != null);
    checkArgument(config != null);
    this.dccLocking = dccLocking;
    this.fs = fs;
    this.config = config;
    registerModelClasses(Release.class);
  }

  /**
   * Returns a list of {@code Release}s with their @{code Submission} filtered based on the user's privilege on
   * projects.
   */
  public List<Release> getFilteredReleases(Subject subject) {
    log.debug("getting releases for {}", subject.getPrincipal());

    List<Release> releases = listReleases();
    log.debug("#releases: ", releases.size());

    // filter out all the submissions that the current user can not see
    for (Release release : releases) {
      List<Submission> newSubmissions = Lists.newArrayList();
      for (Submission submission : release.getSubmissions()) {
        String projectKey = submission.getProjectKey();
        if (subject.isPermitted(AuthorizationPrivileges.projectViewPrivilege(projectKey))) {
          newSubmissions.add(submission);
        }
      }
      release.getSubmissions().clear(); // TODO: should we manipulate release this way? consider creating DTO?
      release.getSubmissions().addAll(newSubmissions);
    }
    log.debug("#releases visible: ", releases.size());

    return releases;
  }

  public void createInitialRelease(Release initRelease) {
    // check for init release name
    if (!NameValidator.validateEntityName(initRelease.getName())) {
      throw new InvalidNameException(initRelease.getName());
    }
    String dictionaryVersion = initRelease.getDictionaryVersion();
    if (dictionaryVersion == null) {
      throw new ReleaseException("Dictionary version must not be null!");
    } else if (getDictionaryForVersion(dictionaryVersion) == null) {
      throw new ReleaseException("Specified dictionary version not found in DB: " + dictionaryVersion);
    }
    // Just use name and dictionaryVersion from incoming json
    Release nextRelease = new Release(initRelease.getName());
    nextRelease.setDictionaryVersion(dictionaryVersion);
    datastore().save(nextRelease);
    // after initial release, create initial file system
    Set<String> projects = Sets.newHashSet();
    fs.ensureReleaseFilesystem(nextRelease, projects);
  }

  public boolean hasNextRelease() {
    return this.query().where(QRelease.release.state.eq(ReleaseState.OPENED)).singleResult() != null;
  }

  public Release getFromName(String releaseName) {
    Release release = this.query().where(QRelease.release.name.eq(releaseName)).uniqueResult();

    return release;
  }

  /**
   * Optionally returns a {@code ReleaseView} matching the given name, and for which {@code Submission}s are filtered
   * based on the user's privileges.
   */
  public Optional<ReleaseView> getFilteredReleaseView(String releaseName, Subject user) {
    Release release = this.query().where(QRelease.release.name.eq(releaseName)).uniqueResult();
    Optional<ReleaseView> releaseView = Optional.absent();
    if (release != null) {
      // populate project name for submissions
      List<Project> projects = this.getProjects(release, user);
      List<LiteProject> liteProjects = buildLiteProjects(projects);
      Map<String, List<SubmissionFile>> submissionFilesMap = buildSubmissionFilesMap(releaseName, release);
      releaseView = Optional.of(new ReleaseView(release, liteProjects, submissionFilesMap));
    }
    return releaseView;
  }

  /**
   * Returns the {@code NextRelease} (guaranteed not to be null if returned).
   */
  public NextRelease getNextRelease() throws IllegalReleaseStateException {
    Release nextRelease = this.query().where(QRelease.release.state.eq(ReleaseState.OPENED)).singleResult();
    return new NextRelease(dccLocking, checkNotNull(nextRelease, "There is no next release in the database."),
        morphia(), datastore(), this.fs);
  }

  /**
   * Returns the current dictionary.
   * <p>
   * This is the dictionary, open or not, that the {@code NextRelease}'s {@code Release} points to.
   */
  public Dictionary getNextDictionary() {
    NextRelease nextRelease = getNextRelease();
    Release release = checkNotNull(nextRelease, "There are currently no open releases...").getRelease();
    String version = checkNotNull(release).getDictionaryVersion();
    Dictionary dictionary = getDictionaryFromVersion(checkNotNull(version));
    return dictionary;
  }

  /**
   * Returns a non-null list of @{code HasRelease} (possibly empty)
   */
  public List<HasRelease> list() {
    List<HasRelease> list = new ArrayList<HasRelease>();

    for (Release release : listReleases()) {
      if (release.getState() == ReleaseState.OPENED) {
        list.add(new NextRelease(dccLocking, release, morphia(), datastore(), fs));
      } else {
        list.add(new CompletedRelease(release, morphia(), datastore(), fs));
      }
    }

    return list;
  }

  public CompletedRelease getCompletedRelease(String releaseName) throws IllegalReleaseStateException {
    MongodbQuery<Release> query =
        this.where(QRelease.release.state.eq(ReleaseState.COMPLETED).and(QRelease.release.name.eq(releaseName)));
    Release release = query.uniqueResult();
    if (release == null) {
      throw new IllegalArgumentException("release " + releaseName + " is not complete");
    }
    return new CompletedRelease(release, morphia(), datastore(), fs);
  }

  public Submission getSubmission(String releaseName, String projectKey) {
    Release release = this.where(QRelease.release.name.eq(releaseName)).uniqueResult();
    checkArgument(release != null);

    return this.getSubmission(release, projectKey);
  }

  public DetailedSubmission getDetailedSubmission(String releaseName, String projectKey) {
    Submission submission = this.getSubmission(releaseName, projectKey);
    LiteProject liteProject = new LiteProject(checkNotNull(this.getProject(projectKey)));
    DetailedSubmission detailedSubmission = new DetailedSubmission(submission, liteProject);
    detailedSubmission.setSubmissionFiles(getSubmissionFiles(releaseName, projectKey));
    return detailedSubmission;
  }

  public List<CompletedRelease> getCompletedReleases() throws IllegalReleaseStateException {
    List<CompletedRelease> completedReleases = new ArrayList<CompletedRelease>();

    for (Release release : listReleases(Optional.<Predicate> of(QRelease.release.state.eq(ReleaseState.COMPLETED)))) {
      completedReleases.add(new CompletedRelease(release, morphia(), datastore(), fs));
    }

    return completedReleases;
  }

  private Submission getSubmission(Release release, String projectKey) {
    for (Submission submission : release.getSubmissions()) {
      if (submission.getProjectKey().equals(projectKey)) {
        return submission;
      }
    }

    throw new ReleaseException(String.format("there is no project \"%s\" associated with release \"%s\"", projectKey,
        release.getName()));
  }

  public Release getRelease(String releaseName) {
    return this.where(QRelease.release.name.eq(releaseName)).uniqueResult();
  }

  public List<String> getSignedOff() {
    return this.getSubmission(SubmissionState.SIGNED_OFF);
  }

  public void signOff(Release nextRelease, List<String> projectKeys, String user) //
      throws InvalidStateException, DccModelOptimisticLockException {

    String nextReleaseName = nextRelease.getName();
    log.info("signing off {} for {}", projectKeys, nextReleaseName);

    // update release object
    SubmissionState expectedState = SubmissionState.VALID;
    nextRelease.removeFromQueue(projectKeys);
    for (String projectKey : projectKeys) {
      Submission submission = fetchAndCheckSubmission(nextRelease, projectKey, expectedState);
      submission.setState(SubmissionState.SIGNED_OFF);
    }

    updateRelease(nextReleaseName, nextRelease);

    // TODO: synchronization (DCC-685), may require cleaning up the FS abstraction (do we really need the project object
    // or is the projectKey sufficient?)
    // remove .validation folder from the Submission folder
    ReleaseFileSystem releaseFS = this.fs.getReleaseFilesystem(nextRelease);
    List<Project> projects = this.getProjects(projectKeys);
    for (Project project : projects) {
      SubmissionDirectory submissionDirectory = releaseFS.getSubmissionDirectory(project.getKey());
      submissionDirectory.removeValidationDir();
    }

    // after sign off, send a email to DCC support
    MailUtils.email(this.config, //
        config.getString(MailUtils.NORMAL_FROM), //
        config.getString(MailUtils.AUTOMATIC_SUPPORT_RECIPIENT), //
        String.format("Signed off Projects: %s", projectKeys), //
        String.format(config.getString(MailUtils.SIGNOFF_BODY), user, projectKeys, nextReleaseName));

    log.info("signed off {} for {}", projectKeys, nextReleaseName);
  }

  public void deleteQueuedRequest() {
    log.info("emptying queue");

    SubmissionState newState = SubmissionState.NOT_VALIDATED;
    Release release = getNextRelease().getRelease();
    List<String> projectKeys = release.getQueuedProjectKeys(); // TODO: what if nextrelease changes in the meantime?

    updateSubmisions(projectKeys, newState); // FIXME: DCC-901
    release.emptyQueue();

    this.dbUpdateSubmissions(release.getName(), release.getQueue(), projectKeys, newState); // FIXME: DCC-901
    for (String projectKey : projectKeys) { // See spec at
                                            // https://wiki.oicr.on.ca/display/DCCSOFT/Concurrency#Concurrency-Submissionstatesresetting
      resetValidationFolder(projectKey, release);
    }
  }

  public void queue(Release nextRelease, List<QueuedProject> queuedProjects) //
      throws InvalidStateException, DccModelOptimisticLockException {
    String nextReleaseName = nextRelease.getName();
    log.info("enqueuing {} for {}", queuedProjects, nextReleaseName);

    // update release object
    SubmissionState expectedState = SubmissionState.NOT_VALIDATED;
    nextRelease.enqueue(queuedProjects);
    for (QueuedProject queuedProject : queuedProjects) {
      String projectKey = queuedProject.getKey();
      Submission submission = fetchAndCheckSubmission(nextRelease, projectKey, expectedState);
      submission.setState(SubmissionState.QUEUED);
    }

    updateRelease(nextReleaseName, nextRelease);
    log.info("enqueued {} for {}", queuedProjects, nextReleaseName);
  }

  /**
   * Attempts to set the given project to VALIDATING.<br>
   * <p>
   * This method is robust enough to handle rare cases like when:<br>
   * - the queue was emptied by an admin in another thread (TODO: complete, this is only partially supported now)<br>
   * - the optimistic lock on Release cannot be obtained (retries a number of time before giving up)<br>
   */
  public void dequeueToValidating(QueuedProject nextProject) {
    String nextProjectKey = nextProject.getKey();
    log.info("Attempting to set {} to validating", nextProjectKey);

    String nextReleaseName = null;
    SubmissionState expectedState = SubmissionState.QUEUED;

    int attempts = 0;
    int MAX_ATTEMPTS = 10; // 10 attempts should be sufficient to obtain a lock (otherwise the problem is probably not
                           // recoverable - deadlock or other)
    while (attempts < MAX_ATTEMPTS) {

      try {
        Release nextRelease = getNextRelease().getRelease();
        nextReleaseName = nextRelease.getName();
        log.info("Dequeuing {} to validating for {}", nextProjectKey, nextReleaseName);

        // actually dequeue the project
        QueuedProject dequeuedProject = nextRelease.dequeueProject();
        String dequeuedProjectKey = dequeuedProject.getKey();
        if (dequeuedProjectKey.equals(nextProjectKey) == false) { // not recoverable: TODO: create dedicated exception?
          throw new ReleaseException("Mismatch: " + dequeuedProjectKey + " != " + nextProjectKey);
        }

        // update release object
        Submission submission = getSubmissionByName(nextRelease, nextProjectKey); // can't be null
        SubmissionState currentState = submission.getState();
        SubmissionState destinationState = SubmissionState.VALIDATING;
        if (expectedState != currentState) {
          throw new ReleaseException( // not recoverable
              "Project " + nextProjectKey + " is not " + expectedState + " (" + currentState
                  + " instead), cannot set to " + destinationState);
        }
        submission.setState(destinationState);

        // update corresponding database entity
        updateRelease(nextReleaseName, nextRelease);

        log.info("Dequeued {} to validating state for {}", nextProjectKey, nextReleaseName);
        break;
      } catch (DccModelOptimisticLockException e) {
        attempts++;
        log.warn(
            "There was a concurrency issue while attempting to set {} to validating state for release {}, number of attempts: {}",
            new Object[] { nextProjectKey, nextReleaseName, attempts });
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS); // TODO: cleanup - use Executor instead?
      }
    }
    if (attempts >= MAX_ATTEMPTS) {
      String message =
          String.format("failed to validate project %s, could never acquire lock: please contact %s", nextProjectKey,
              config.getString(MailUtils.ADMIN_RECIPIENT));
      MailUtils.email(this.config, config.getString(MailUtils.PROBLEM_FROM),
          config.getString(MailUtils.ADMIN_RECIPIENT), message, message);
      throw new DccConcurrencyException(message);
    }
  }

  /**
   * Attempts to resolve the given project, if the project is found the given state is set for it.<br>
   * <p>
   * This method is robust enough to handle rare cases like when:<br>
   * - the queue was emptied by an admin in another thread (TODO: complete, this is only partially supported now)<br>
   * - the optimistic lock on Release cannot be obtained (retries a number of time before giving up)<br>
   */
  public void resolve(String projectKey, SubmissionState destinationState) { // TODO: avoid code duplication (see method
                                                                             // above)
    checkArgument(SubmissionState.VALID == destinationState || SubmissionState.INVALID == destinationState
        || SubmissionState.ERROR == destinationState);

    log.info("attempting to resolve {} (as {})", projectKey, destinationState);

    String nextReleaseName = null;
    SubmissionState expectedState = SubmissionState.VALIDATING;

    int attempts = 0;
    int MAX_ATTEMPTS = 10; // 10 attempts should be sufficient to obtain a lock (otherwise the problem is probably not
                           // recoverable - deadlock or other)
    while (attempts < MAX_ATTEMPTS) {

      try {
        Release nextRelease = getNextRelease().getRelease();
        nextReleaseName = nextRelease.getName();
        log.info("Resolving {} (as {}) for {}", new Object[] { projectKey, destinationState, nextReleaseName });

        Submission submission = getSubmissionByName(nextRelease, projectKey); // can't be null
        SubmissionState currentState = submission.getState();
        if (expectedState != currentState) {
          throw new ReleaseException( // not recoverable
              "project " + projectKey + " is not " + expectedState + " (" + currentState + " instead), cannot set to "
                  + destinationState);
        }
        submission.setState(destinationState);

        // update corresponding database entity
        updateRelease(nextReleaseName, nextRelease);

        log.info("Resolved {} for {}", projectKey, nextReleaseName);
        break;
      } catch (DccModelOptimisticLockException e) {
        attempts++;
        log.warn(
            "There was a concurrency issue while attempting to resolve {} (as {}) for release {}, number of attempts: {}",
            new Object[] { projectKey, destinationState, nextReleaseName, attempts });
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS); // TODO: cleanup - use Executor instead?
      }
    }
    if (attempts >= MAX_ATTEMPTS) {
      String message =
          String.format("Failed to resolve project %s, could never acquire lock: please contact %s", projectKey,
              config.getString(MailUtils.ADMIN_RECIPIENT));
      MailUtils.email(this.config, config.getString(MailUtils.PROBLEM_FROM),
          config.getString(MailUtils.ADMIN_RECIPIENT), message, message);
      throw new DccConcurrencyException(message);
    }
  }

  /**
   * Updates a release name and/or dictionary version.
   * <p>
   * Does not allow to update submissions per se, {@code ProjectService.addProject()} must be used instead.
   * <p>
   * This MUST reset submission states.
   * <p>
   * This method is not included in NextRelease because of its dependence on methods from NextRelease (we may reconsider
   * in the future) - see comments in DCC-245
   */
  public Release update(String newReleaseName, String newDictionaryVersion) {

    Release release = getNextRelease().getRelease();
    String oldReleaseName = release.getName();
    String oldDictionaryVersion = release.getDictionaryVersion();
    checkState(release.getState() == ReleaseState.OPENED);

    boolean sameName = oldReleaseName.equals(newReleaseName);
    boolean sameDictionary = oldDictionaryVersion.equals(newDictionaryVersion);

    if (!NameValidator.validateEntityName(newReleaseName)) {
      throw new ReleaseException("Updated release name " + newReleaseName + " is not valid");
    }

    if (sameName == false && getFromName(newReleaseName) != null) {
      throw new ReleaseException("New release name " + newReleaseName + " conflicts with an existing release");
    }

    if (newDictionaryVersion == null) {
      throw new ReleaseException("Release must have associated dictionary before being updated");
    }

    if (sameDictionary == false && getDictionaryFromVersion(newDictionaryVersion) == null) {
      throw new ReleaseException("Release must point to an existing dictionary, no match for " + newDictionaryVersion);
    }

    // Update release object and database entity (top-level entity only)
    log.info("Updating release {} with {} and {}" + (sameDictionary ? " and emptying queue" : ""), //
        new Object[] { oldReleaseName, newReleaseName, newDictionaryVersion });
    release.setName(newReleaseName);
    release.setDictionaryVersion(newDictionaryVersion);
    if (sameDictionary == false) {
      release.emptyQueue();
    }
    UpdateResults<Release> releaseUpdate = datastore().update( //
        datastore().createQuery(Release.class) //
            .filter("name = ", oldReleaseName), //
        datastore().createUpdateOperations(Release.class) //
            .set("name", newReleaseName) //
            .set("dictionaryVersion", newDictionaryVersion) //
            .set("queue", release.getQueue()));
    if (releaseUpdate.getUpdatedCount() != 1) { // Ensure update was successful
      notifyUpdateError(oldReleaseName, on(",").join(newReleaseName, newDictionaryVersion, release.getQueue()));
    }

    // If a new dictionary was specified, reset submissions, TODO: use resetSubmission() instead (DCC-901)!
    if (sameDictionary == false) {
      for (Submission submission : release.getSubmissions()) {
        String projectKey = submission.getProjectKey();
        SubmissionState newSubmissionState = NOT_VALIDATED;
        String report = "report";

        log.info("Setting submission {} to {} and resetting {}", //
            new Object[] { on(".").join(newReleaseName, projectKey), newSubmissionState, report });

        submission.setState(newSubmissionState);
        submission.resetReport();
        UpdateResults<Release> submissionUpdate = datastore().update( //
            datastore().createQuery(Release.class) //
                .filter("name = ", newReleaseName) //
                .filter("submissions.projectKey = ", projectKey), //
            allowDollarSignForMorphiaUpdatesBug(datastore().createUpdateOperations(Release.class)) //
                .set("submissions.$.state", newSubmissionState) //
                .unset(report));
        if (submissionUpdate.getUpdatedCount() != 1) { // Ensure update was successful
          notifyUpdateError(on(".").join(newReleaseName, projectKey), newSubmissionState.name(), report);
        }
      }
    }

    return release;
  }

  public void resetSubmissions(final String releaseName, final Iterable<String> projectKeys) {
    for (String projectKey : projectKeys) {
      resetSubmission(releaseName, projectKey);
    }
  }

  /**
   * Resets submission to NOT_VALIDATED and empty report.
   * <p>
   * Note that one must also empty the .validation directory for cascading to re-run fully.
   * <p>
   * see DCC-901
   */
  public void resetSubmission(final String releaseName, final String projectKey) {
    log.info("resetting submission for project {}", projectKey);

    // Reset state and report in database (TODO: queue + currently validating? - DCC-906)
    Release release = datastore().findAndModify( //
        datastore().createQuery(Release.class) //
            .filter("name = ", releaseName) //
            .filter("submissions.projectKey = ", projectKey), //
        allowDollarSignForMorphiaUpdatesBug(datastore().createUpdateOperations(Release.class)) //
            .set("submissions.$.state", SubmissionState.NOT_VALIDATED) //
            .unset("submissions.$.report"), false);

    Submission submission = release.getSubmission(projectKey);
    if (submission == null || submission.getState() != SubmissionState.NOT_VALIDATED || submission.getReport() != null) {
      // TODO: DCC-902 (optimistic lock potential problem: what if this actually happens? - add a retry?)
      log.error("If you see this, then DCC-902 MUST be addressed.");
      throw new ReleaseException("Resetting submission failed for project " + projectKey);
    }

    // Empty .validation dir else cascade may not rerun
    resetValidationFolder(projectKey, release); // TODO: see note in method javadoc
  }

  /**
   * TODO: only taken out of resetSubmission() until DCC-901 is done (to allow code that calls deprecated methods
   * instead of resetSubmission() to still be able to empty those directories)
   */
  private void resetValidationFolder(final String projectKey, Release release) {
    fs.getReleaseFilesystem(release).resetValidationFolder(projectKey);
  }

  /**
   * TODO: should also take care of updating the queue, as the two should always go together
   * <p>
   * deprecation: see DCC-901
   */
  @Deprecated
  private void updateSubmisions(List<String> projectKeys, final SubmissionState state) {
    final String releaseName = getNextRelease().getRelease().getName();
    for (String projectKey : projectKeys) {
      getSubmission(releaseName, projectKey).setState(state);
    }
  }

  /**
   * Updates the queue then the submissions states accordingly
   * <p>
   * Always update queue and submission states together (else state may be inconsistent)
   * <p>
   * TODO: should probably revisit all this as it is not very clean
   * <p>
   * deprecation: see DCC-901
   */
  @Deprecated
  private void dbUpdateSubmissions(String currentReleaseName, List<QueuedProject> queue, List<String> projectKeys,
      SubmissionState newState) {
    checkArgument(currentReleaseName != null);
    checkArgument(queue != null);

    datastore().update( //
        datastore().createQuery(Release.class) //
            .filter("name = ", currentReleaseName), //
        datastore().createUpdateOperations(Release.class) //
            .set("queue", queue));

    for (String projectKey : projectKeys) {
      updateSubmission(currentReleaseName, newState, projectKey);
    }
  }

  private void updateSubmission(String currentReleaseName, SubmissionState newState, String projectKey) {
    datastore().update( //
        datastore().createQuery(Release.class) //
            .filter("name = ", currentReleaseName) //
            .filter("submissions.projectKey = ", projectKey), //
        allowDollarSignForMorphiaUpdatesBug(datastore().createUpdateOperations(Release.class)) //
            .set("submissions.$.state", newState));
  }

  public void updateSubmission(String currentReleaseName, Submission submission) {
    this.updateSubmission(currentReleaseName, submission.getState(), submission.getProjectKey());
  }

  public void updateSubmissionReport(String releaseName, String projectKey, SubmissionReport report) {

    UpdateResults<Release> update = datastore().update( //
        datastore().createQuery(Release.class) //
            .filter("name = ", releaseName) //
            .filter("submissions.projectKey = ", projectKey), //
        allowDollarSignForMorphiaUpdatesBug(datastore().createUpdateOperations(Release.class)) //
            .set("submissions.$.report", report));

    int updatedCount = update.getUpdatedCount();
    if (updatedCount != 1) { // Only to help diagnosis for now, we're unsure when that happens (DCC-848)
      log.error("Setting submission reports {} failed for {}.{}", new Object[] { (report == null ? null : report
          .getSchemaReports().size()), releaseName, projectKey }, new IllegalStateException());
    }
  }

  public List<Project> getProjects(Release release, Subject user) {
    List<String> projectKeys = new ArrayList<String>();
    for (Submission submission : release.getSubmissions()) {
      if (user.isPermitted(AuthorizationPrivileges.projectViewPrivilege(submission.getProjectKey()))) {
        projectKeys.add(submission.getProjectKey());
      }
    }
    return this.getProjects(projectKeys);
  }

  private List<Project> getProjects(List<String> projectKeys) {
    MorphiaQuery<Project> query = new MorphiaQuery<Project>(morphia(), datastore(), QProject.project);
    return query.where(QProject.project.key.in(projectKeys)).list();
  }

  private Project getProject(String projectKey) {
    MorphiaQuery<Project> query = new MorphiaQuery<Project>(morphia(), datastore(), QProject.project);
    return query.where(QProject.project.key.eq(projectKey)).uniqueResult();
  }

  public List<SubmissionFile> getSubmissionFiles(String releaseName, String projectKey) {
    Release release = this.where(QRelease.release.name.eq(releaseName)).singleResult();
    if (release == null) {
      throw new ReleaseException("No such release");
    }

    Dictionary dict = this.getDictionaryFromVersion(release.getDictionaryVersion());

    if (dict == null) {
      throw new ReleaseException("No Dictionary " + release.getDictionaryVersion());
    }

    List<SubmissionFile> submissionFileList = new ArrayList<SubmissionFile>();
    Path buildProjectStringPath = new Path(this.fs.buildProjectStringPath(release, projectKey));
    for (Path path : HadoopUtils.lsFile(this.fs.getFileSystem(), buildProjectStringPath)) { // TODO: use DccFileSystem
                                                                                            // abstraction instead
      submissionFileList.add(new SubmissionFile(path, fs.getFileSystem(), dict));
    }
    return submissionFileList;
  }

  private Submission fetchAndCheckSubmission(Release nextRelease, String projectKey, SubmissionState expectedState)
      throws InvalidStateException {
    Submission submission = getSubmissionByName(nextRelease, projectKey);
    String errorMessage;
    if (submission == null) {
      errorMessage = "project " + projectKey + " cannot be found";
      log.error(errorMessage);
      throw new InvalidStateException(ServerErrorCode.PROJECT_KEY_NOT_FOUND, errorMessage);
    }
    SubmissionState currentState = submission.getState();
    if (expectedState != currentState) {
      errorMessage = "project " + projectKey + " is not " + expectedState + " (" + currentState + " instead)";
      log.error(errorMessage);
      throw new InvalidStateException(ServerErrorCode.INVALID_STATE, errorMessage, currentState);
    }
    return submission;
  }

  private List<String> getSubmission(final SubmissionState state) {
    List<String> projectKeys = new ArrayList<String>();
    List<Submission> submissions = this.getNextRelease().getRelease().getSubmissions();
    for (Submission submission : submissions) {
      if (state.equals(submission.getState())) {
        projectKeys.add(submission.getProjectKey());
      }
    }
    return projectKeys;
  }

  /**
   * Updates the release with the given name, there must be a matching release.<br>
   * 
   * Concurrency is handled with <code>{@link BaseEntity#internalVersion}</code> (optimistic lock).
   * 
   * @throws DccModelOptimisticLockException if optimistic lock fails
   * @throws ReleaseException if the update fails for other reasons (probably not recoverable)
   */
  private void updateRelease(String originalReleaseName, Release updatedRelease) throws DccModelOptimisticLockException {
    UpdateResults<Release> update = null;
    try {
      update = datastore().updateFirst( //
          datastore().createQuery(Release.class) //
              .filter("name = ", originalReleaseName), //
          updatedRelease, false);
    } catch (ConcurrentModificationException e) { // see method comments for why this could be thrown
      log.warn("a possibly recoverable concurrency issue arose when trying to update release {}", originalReleaseName);
      throw new DccModelOptimisticLockException(e);
    }
    if (update == null || update.getHadError()) {
      log.error("an unrecoverable error happenend when trying to update release {}", originalReleaseName);
      throw new ReleaseException(String.format("failed to update release %s", originalReleaseName));
    }
  }

  /**
   * Attempts to retrieve a submission for the given project key provided from the release object provided (no database
   * call).
   * <p>
   * Throws a {@code ReleaseException} if not matching submission is found.
   */
  private Submission getSubmissionByName(Release release, String projectKey) {
    Submission submission = release.getSubmission(projectKey);
    if (submission == null) {
      throw new ReleaseException(String.format("there is no project \"%s\" associated with release \"%s\"", projectKey,
          release.getName()));
    }
    return submission;
  }

  // TODO: figure out difference with method below
  private Dictionary getDictionaryFromVersion(String version) { // also found in DictionaryService - see comments in
                                                                // DCC-245
    return new MorphiaQuery<Dictionary>(morphia(), datastore(), QDictionary.dictionary).where(
        QDictionary.dictionary.version.eq(version)).singleResult();
  }

  private Dictionary getDictionaryForVersion(String dictionaryVersion) {
    return this.datastore().createQuery(Dictionary.class) //
        .filter("version", dictionaryVersion).get();
  }

  private List<LiteProject> buildLiteProjects(List<Project> projects) {
    List<LiteProject> liteProjects = newArrayList();
    for (Project project : projects) {
      liteProjects.add(new LiteProject(project));
    }
    return copyOf(liteProjects);
  }

  private Map<String, List<SubmissionFile>> buildSubmissionFilesMap(String releaseName, Release release) {
    Map<String, List<SubmissionFile>> submissionFilesMap = Maps.newLinkedHashMap();
    for (String projectKey : release.getProjectKeys()) {
      submissionFilesMap.put(projectKey, getSubmissionFiles(releaseName, projectKey));
    }
    return ImmutableMap.copyOf(submissionFilesMap);
  }

  /**
   * This is currently necessary in order to use the <i>field.$.nestedField</i> notation in updates. Otherwise one gets
   * an error like <i>
   * "The field '$' could not be found in 'org.icgc.dcc.submission.release.model.Release' while validating - submissions.$.state; if you wish to continue please disable validation."
   * </i>
   * <p>
   * For more information, see
   * http://groups.google.com/group/morphia/tree/browse_frm/month/2011-01/489d5b7501760724?rnum
   * =31&_done=/group/morphia/browse_frm/month/2011-01?
   */
  private static <T> UpdateOperations<T> allowDollarSignForMorphiaUpdatesBug(UpdateOperations<T> updateOperations) {
    return updateOperations.disableValidation();
  }

  private void notifyUpdateError(String filter, String setValues) {
    notifyUpdateError(filter, setValues, null);
  }

  /**
   * To notify us that an update failed.
   */
  private void notifyUpdateError(String filter, String setValues, String unsetValues) {
    log.error("Unable to update the release (maybe a lock problem)?", new IllegalStateException());
    email(this.config, //
        config.getString(MailUtils.PROBLEM_FROM), //
        config.getString(MailUtils.AUTOMATIC_SUPPORT_RECIPIENT), //
        format("Automatic email - Failure update"), //
        format("filter: %s, set values: %s, unset values: %s", //
            filter, setValues, unsetValues));
  }

  private List<Release> listReleases() {
    return listReleases(Optional.<Predicate> absent());
  }

  /**
   * Ignoring releases whose name is starting with a specific prefix (see https://jira.oicr.on.ca/browse/DCC-1409 for
   * more details).
   */
  private List<Release> listReleases(Optional<Predicate> mysemaPredicate) {
    MongodbQuery<Release> query = mysemaPredicate.isPresent() ?
        where(mysemaPredicate.get()) :
        query();
    return filterOutFakeReleasesForETL(query.list());
  }

  /**
   * Filters out fake releases used by the ETL component (see https://jira.oicr.on.ca/browse/DCC-1409 for more details).
   */
  public static List<Release> filterOutFakeReleasesForETL(List<Release> list) {
    return newArrayList(Iterables.filter(list,
        new com.google.common.base.Predicate<Release>() { // guava predicate (not mysema)

          @Override
          public boolean apply(Release release) {
            String releaseName = release.getName();
            return isFakeReleaseForETL(releaseName) == false;
          }
        }));
  }

  /**
   * Returns true if the release should be ignored (see https://jira.oicr.on.ca/browse/DCC-1409 for more details).
   */
  private static boolean isFakeReleaseForETL(String releaseName) {
    return releaseName.startsWith(SPECIAL_RELEASE_PREFIX);
  }

}