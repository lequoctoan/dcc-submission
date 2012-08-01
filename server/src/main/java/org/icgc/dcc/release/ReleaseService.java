package org.icgc.dcc.release;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.hadoop.fs.Path;
import org.icgc.dcc.core.model.Project;
import org.icgc.dcc.core.model.QProject;
import org.icgc.dcc.core.morphia.BaseMorphiaService;
import org.icgc.dcc.dictionary.model.Dictionary;
import org.icgc.dcc.dictionary.model.QDictionary;
import org.icgc.dcc.filesystem.DccFileSystem;
import org.icgc.dcc.filesystem.SubmissionFile;
import org.icgc.dcc.filesystem.hdfs.HadoopUtils;
import org.icgc.dcc.release.model.QRelease;
import org.icgc.dcc.release.model.Release;
import org.icgc.dcc.release.model.ReleaseState;
import org.icgc.dcc.release.model.ReleaseView;
import org.icgc.dcc.release.model.Submission;
import org.icgc.dcc.release.model.SubmissionState;
import org.icgc.dcc.validation.report.SubmissionReport;
import org.icgc.dcc.web.validator.InvalidNameException;
import org.icgc.dcc.web.validator.NameValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.google.code.morphia.query.Query;
import com.google.code.morphia.query.UpdateOperations;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.mysema.query.mongodb.MongodbQuery;
import com.mysema.query.mongodb.morphia.MorphiaQuery;

public class ReleaseService extends BaseMorphiaService<Release> {

  private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);

  private final DccFileSystem fs;

  @Inject
  public ReleaseService(Morphia morphia, Datastore datastore, DccFileSystem fs) {
    super(morphia, datastore, QRelease.release);
    this.fs = fs;
    registerModelClasses(Release.class);
  }

  public void createInitialRelease(Release initRelease) {
    // check for init release name
    if(!NameValidator.validate(initRelease.getName())) {
      throw new InvalidNameException(initRelease.getName());
    }
    String dictionaryVersion = initRelease.getDictionaryVersion();
    if(dictionaryVersion == null) {
      throw new ReleaseException("Dictionary version must not be null!");
    } else if(this.datastore().createQuery(Dictionary.class).filter("version", dictionaryVersion).get() == null) {
      throw new ReleaseException("Specified dictionary version not found in DB: " + dictionaryVersion);
    }
    datastore().save(initRelease);
    Set<String> projectKeys = new LinkedHashSet<String>();
    for(Submission submission : initRelease.getSubmissions()) {
      projectKeys.add(submission.getProjectKey());
    }

    this.fs.createReleaseFilesystem(initRelease, projectKeys);
  }

  public boolean hasNextRelease() {
    return this.query().where(QRelease.release.state.eq(ReleaseState.OPENED)).singleResult() != null;
  }

  public Release getFromName(String releaseName) {
    Release release = this.query().where(QRelease.release.name.eq(releaseName)).uniqueResult();

    return release;
  }

  public ReleaseView getReleaseView(String releaseName) {
    Release release = this.query().where(QRelease.release.name.eq(releaseName)).uniqueResult();
    ReleaseView releaseView = new ReleaseView(release);
    // populate project name for submissions
    List<Project> projects = this.getProjects(release);
    releaseView.updateProjects(projects);
    return releaseView;
  }

  public NextRelease getNextRelease() throws IllegalReleaseStateException {
    Release nextRelease = this.query().where(QRelease.release.state.eq(ReleaseState.OPENED)).singleResult();
    if(nextRelease == null) {
      throw new IllegalStateException("no next release");
    }
    return new NextRelease(nextRelease, datastore(), this.fs);
  }

  public List<HasRelease> list() {
    List<HasRelease> list = new ArrayList<HasRelease>();

    MongodbQuery<Release> query = this.query();

    for(Release release : query.list()) {
      list.add(new BaseRelease(release));
    }

    return list;
  }

  public List<CompletedRelease> getCompletedReleases() throws IllegalReleaseStateException {
    List<CompletedRelease> completedReleases = new ArrayList<CompletedRelease>();

    MongodbQuery<Release> query = this.where(QRelease.release.state.eq(ReleaseState.COMPLETED));

    for(Release release : query.list()) {
      completedReleases.add(new CompletedRelease(release));
    }

    return completedReleases;
  }

  public Submission getSubmission(String releaseName, String projectKey) {
    Release release = this.where(QRelease.release.name.eq(releaseName)).uniqueResult();
    checkArgument(release != null);

    return this.getSubmission(release, projectKey);
  }

  private Submission getSubmission(Release release, String projectKey) {
    Submission result = null;
    for(Submission submission : release.getSubmissions()) {
      if(submission.getProjectKey().equals(projectKey)) {
        result = submission;
        break;
      }
    }

    if(result == null) {
      throw new ReleaseException(String.format("there is no project \"%s\" associated with release \"%s\"", projectKey,
          release.getName()));
    }

    return result;
  }

  public List<String> getSignedOff() {
    return this.getSubmission(SubmissionState.SIGNED_OFF);
  }

  public void signOff(List<String> projectKeys) {
    log.info("signinng off: {}", projectKeys);

    SubmissionState newState = SubmissionState.SIGNED_OFF;
    Release release = getNextRelease().getRelease();

    updateSubmisions(projectKeys, newState);
    release.removeFromQueue(projectKeys);

    this.dbUpdateSubmissions(release.getName(), release.getQueue(), projectKeys, newState);
  }

  public void deleteQueuedRequest() {
    log.info("emptying queue");

    SubmissionState newState = SubmissionState.NOT_VALIDATED;
    Release release = getNextRelease().getRelease();
    List<String> projectKeys = release.getQueue(); // TODO: what if nextrelease changes in the meantime?

    updateSubmisions(projectKeys, newState);
    release.emptyQueue();

    this.dbUpdateSubmissions(release.getName(), release.getQueue(), projectKeys, newState);
  }

  public void queue(List<String> projectKeys) {
    log.info("enqueuing: {}", projectKeys);

    SubmissionState newState = SubmissionState.QUEUED;
    Release release = this.getNextRelease().getRelease();

    updateSubmisions(projectKeys, newState);
    release.enqueue(projectKeys);

    this.dbUpdateSubmissions(release.getName(), release.getQueue(), projectKeys, newState);
  }

  public boolean hasProjectKey(List<String> projectKeys) {
    for(String projectKey : projectKeys) {
      if(!this.hasProjectKey(projectKey)) {
        return false;
      }
    }
    return true;
  }

  public boolean hasProjectKey(String projectKey) {
    Release nextRelease = this.getNextRelease().getRelease();
    for(Submission submission : nextRelease.getSubmissions()) {
      if(submission.getProjectKey().equals(projectKey)) {
        return true;
      }
    }
    return false;
  }

  public Optional<String> dequeue(String projectKey, boolean valid) {
    log.info("dequeuing: {}", projectKey);

    SubmissionState newState = valid ? SubmissionState.VALID : SubmissionState.INVALID;
    Release release = this.getNextRelease().getRelease();

    Optional<String> dequeued = release.nextInQueue();
    if(dequeued.isPresent() && dequeued.get().equals(projectKey)) {
      List<String> projectKeys = Arrays.asList(projectKey);
      dequeued = release.dequeue();
      if(dequeued.isPresent() && dequeued.get().equals(projectKey)) { // could still have changed
        updateSubmisions(projectKeys, newState);
        this.dbUpdateSubmissions(release.getName(), release.getQueue(), projectKeys, newState);
      }
    }

    return dequeued;
  }

  /**
   * Does not allow to update submissions, {@code ProjectService.addProject()} must be used instead
   */
  public Release update(Release updatedRelease) { // This method is not included in NextRelease because of its
                                                  // dependence on methods from NextRelease (we may reconsider in the
                                                  // future) - see comments in DCC-245
    checkArgument(updatedRelease != null);

    String updatedName = updatedRelease.getName();
    String updatedDictionaryVersion = updatedRelease.getDictionaryVersion();

    Release oldRelease = getNextRelease().getRelease();
    String oldName = oldRelease.getName();
    String oldDictionaryVersion = oldRelease.getDictionaryVersion();
    checkState(oldRelease.getState() == ReleaseState.OPENED);

    boolean sameName = oldName.equals(updatedName);
    boolean sameDictionary = oldDictionaryVersion.equals(updatedDictionaryVersion);

    if(!NameValidator.validate(updatedName)) {
      throw new ReleaseException("Updated release name " + updatedName + " is not valid");
    }

    if(sameName == false && getFromName(updatedName) != null) {
      throw new ReleaseException("New release name " + updatedName + " conflicts with an existing release");
    }

    if(updatedDictionaryVersion == null) {
      throw new ReleaseException("Release must have associated dictionary before being updated");
    }

    if(sameDictionary == false && getDictionaryFromVersion(updatedDictionaryVersion) == null) {
      throw new ReleaseException("Release must point to an existing dictionary, no match for "
          + updatedDictionaryVersion);
    }

    // only TWO parameters can be updated for now (though updating the dictionary resets all the submission states)
    oldRelease.setName(updatedName);
    oldRelease.setDictionaryVersion(updatedDictionaryVersion);
    ArrayList<String> oldProjectKeys = Lists.newArrayList(oldRelease.getProjectKeys());
    if(sameDictionary == false) {
      oldRelease.emptyQueue();
      updateSubmisions(oldProjectKeys, SubmissionState.NOT_VALIDATED);
    }

    Query<Release> updateQuery = datastore().createQuery(Release.class)//
        .filter("name = ", oldName);
    UpdateOperations<Release> ops =
        datastore().createUpdateOperations(Release.class).set("name", updatedName)
            .set("dictionaryVersion", updatedDictionaryVersion);
    datastore().update(updateQuery, ops);
    if(sameDictionary == false) {
      dbUpdateSubmissions(updatedName, oldRelease.getQueue(), oldProjectKeys, SubmissionState.NOT_VALIDATED);
    }

    return oldRelease;
  }

  // TODO: should also take care of updating the queue, as the two should always go together
  private void updateSubmisions(List<String> projectKeys, final SubmissionState state) {
    final String releaseName = getNextRelease().getRelease().getName();
    for(String projectKey : projectKeys) {
      getSubmission(releaseName, projectKey).setState(state);
    }
  }

  /**
   * Updates the queue then the submissions states accordingly
   * <p>
   * Always update queue and submission states together (else state may be inconsistent)
   * <p>
   * TODO: should probably revisit all this as it is not very clean
   */
  private void dbUpdateSubmissions(String currentReleaseName, List<String> queue, List<String> projectKeys,
      SubmissionState newState) {
    checkArgument(currentReleaseName != null);
    checkArgument(queue != null);

    Query<Release> updateQuery = datastore().createQuery(Release.class)//
        .filter("name = ", currentReleaseName);
    UpdateOperations<Release> ops = datastore().createUpdateOperations(Release.class).disableValidation()//
        .set("queue", queue);
    datastore().update(updateQuery, ops);

    for(String projectKey : projectKeys) {
      updateSubmission(currentReleaseName, newState, projectKey);
    }
  }

  private void updateSubmission(String currentReleaseName, SubmissionState newState, String projectKey) {
    Query<Release> updateQuery;
    UpdateOperations<Release> ops;
    updateQuery = datastore().createQuery(Release.class)//
        .filter("name = ", currentReleaseName)//
        .filter("submissions.projectKey = ", projectKey);
    ops = datastore().createUpdateOperations(Release.class).disableValidation()//
        .set("submissions.$.state", newState);
    datastore().update(updateQuery, ops);
  }

  public void updateSubmissionReport(String releaseName, String projectKey, SubmissionReport report) {
    Query<Release> updateQuery = datastore().createQuery(Release.class)//
        .filter("name = ", releaseName)//
        .filter("submissions.projectKey = ", projectKey);

    UpdateOperations<Release> ops = datastore().createUpdateOperations(Release.class).disableValidation()//
        .set("submissions.$.report", report);

    datastore().update(updateQuery, ops);
  }

  public List<Project> getProjects(Release release) {
    List<String> projectKeys = new ArrayList<String>();
    for(Submission submission : release.getSubmissions()) {
      projectKeys.add(submission.getProjectKey());
    }
    MorphiaQuery<Project> query = new MorphiaQuery<Project>(morphia(), datastore(), QProject.project);
    return query.where(QProject.project.key.in(projectKeys)).list();
  }

  public List<SubmissionFile> getSubmissionFiles(String releaseName, String projectKey) throws IOException {
    Release release = this.where(QRelease.release.name.eq(releaseName)).singleResult();
    if(release == null) {
      throw new ReleaseException("No such release");
    }

    List<SubmissionFile> submissionFileList = new ArrayList<SubmissionFile>();
    for(Path path : HadoopUtils.lsFile(this.fs.getFileSystem(), this.fs.buildProjectStringPath(release, projectKey))) {
      submissionFileList.add(new SubmissionFile(path, fs.getFileSystem()));
    }
    return submissionFileList;
  }

  private List<String> getSubmission(final SubmissionState state) {
    List<String> projectKeys = new ArrayList<String>();
    List<Submission> submissions = this.getNextRelease().getRelease().getSubmissions();
    for(Submission submission : submissions) {
      if(state.equals(submission.getState())) {
        projectKeys.add(submission.getProjectKey());
      }
    }
    return projectKeys;
  }

  private Dictionary getDictionaryFromVersion(String version) { // also found in DictionaryService - see comments in
                                                                // DCC-245
    return new MorphiaQuery<Dictionary>(morphia(), datastore(), QDictionary.dictionary).where(
        QDictionary.dictionary.version.eq(version)).singleResult();
  }

}
