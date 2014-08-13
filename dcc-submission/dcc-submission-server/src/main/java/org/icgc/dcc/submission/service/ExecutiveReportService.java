/*
 * Copyright (c) 2014 The Ontario Institute for Cancer Research. All rights reserved.                             
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
package org.icgc.dcc.submission.service;

import static com.google.common.collect.ImmutableMap.copyOf;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.icgc.dcc.core.model.Dictionaries.getMapping;
import static org.icgc.dcc.core.model.Dictionaries.getPatterns;
import static org.icgc.dcc.core.model.FileTypes.FileType.SSM_M_TYPE;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import lombok.NonNull;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.hadoop.fs.FileSystem;
import org.icgc.dcc.core.model.FieldNames;
import org.icgc.dcc.core.model.FileTypes.FileType;
import org.icgc.dcc.core.util.InjectionNames;
import org.icgc.dcc.core.util.Jackson;
import org.icgc.dcc.hadoop.dcc.SubmissionInputData;
import org.icgc.dcc.submission.fs.DccFileSystem;
import org.icgc.dcc.submission.reporter.Reporter;
import org.icgc.dcc.submission.reporter.ReporterCollector;
import org.icgc.dcc.submission.reporter.ReporterInput;
import org.icgc.dcc.submission.repository.CodeListRepository;
import org.icgc.dcc.submission.repository.DictionaryRepository;
import org.icgc.dcc.submission.repository.ProjectDataTypeReportRepository;
import org.icgc.dcc.submission.repository.ProjectSequencingStrategyReportRepository;
import org.icgc.dcc.submission.repository.ReleaseRepository;
import org.icgc.submission.summary.ProjectDataTypeReport;
import org.icgc.submission.summary.ProjectSequencingStrategyReport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.inject.Inject;
import com.google.inject.name.Named;

@Slf4j
public class ExecutiveReportService extends AbstractIdleService {

  /**
   * Dependencies.
   */
  private final ReleaseRepository releaseRepository;
  private final DictionaryRepository dictionaryRepository;
  private final CodeListRepository codeListRepository;
  @NonNull
  private final ProjectDataTypeReportRepository projectDataTypeRepository;
  @NonNull
  private final ProjectSequencingStrategyReportRepository projectSequencingStrategyRepository;
  @NonNull
  private final DccFileSystem dccFileSystem;

  /**
   * Configuration.
   */
  @NonNull
  private final Map<String, String> hadoopProperties;

  /**
   * State.
   */
  private final ExecutorService executor = newSingleThreadExecutor();

  @Inject
  public ExecutiveReportService(
      @NonNull final ProjectDataTypeReportRepository projectDataTypeRepository,
      @NonNull final ProjectSequencingStrategyReportRepository projectSequencingStrategyRepository,
      @NonNull final ReleaseRepository releaseRepository,
      @NonNull final DictionaryRepository dictionaryRepository,
      @NonNull final CodeListRepository codeListRepository,
      @NonNull final DccFileSystem dccFileSystem,
      @Named(InjectionNames.HADOOP_PROPERTIES) @NonNull final Map<String, String> hadoopProperties) {
    this.projectDataTypeRepository = projectDataTypeRepository;
    this.projectSequencingStrategyRepository = projectSequencingStrategyRepository;
    this.releaseRepository = releaseRepository;
    this.dictionaryRepository = dictionaryRepository;
    this.codeListRepository = codeListRepository;
    this.dccFileSystem = dccFileSystem;
    this.hadoopProperties = hadoopProperties;
  }

  @Override
  protected void startUp() throws Exception {
    log.info("Starting up...");
  }

  @Override
  protected void shutDown() throws Exception {
    log.info("Shutting down executor...");
    executor.shutdownNow();
    log.info("Finished shutting down executor");
  }

  public List<ProjectDataTypeReport> getProjectDataTypeReport() {
    return projectDataTypeRepository.findAll();
  }

  public List<ProjectDataTypeReport> getProjectDataTypeReport(String releaseName, List<String> projectCodes) {
    return projectDataTypeRepository.find(releaseName, projectCodes);
  }

  public void saveProjectDataTypeReport(ProjectDataTypeReport report) {
    projectDataTypeRepository.upsert(report);
  }

  public void deleteProjectDataTypeReport(final String releaseName) {
    projectDataTypeRepository.deleteByRelease(releaseName);
  }

  public List<ProjectSequencingStrategyReport> getProjectSequencingStrategyReport() {
    return projectSequencingStrategyRepository.findAll();
  }

  public List<ProjectSequencingStrategyReport> getProjectSequencingStrategyReport(String releaseName,
      List<String> projects) {
    return projectSequencingStrategyRepository.find(releaseName, projects);
  }

  public void saveProjectSequencingStrategyReport(ProjectSequencingStrategyReport report) {
    projectSequencingStrategyRepository.upsert(report);
  }

  public void deleteProjectSequencingStrategyReport(final String releaseName) {
    projectSequencingStrategyRepository.deleteByRelease(releaseName);
  }

  private ProjectDataTypeReport getProjectReport(JsonNode report, String releaseName) {
    ProjectDataTypeReport projectDataTypeReport = new ProjectDataTypeReport();
    projectDataTypeReport.setReleaseName(releaseName);
    projectDataTypeReport.setProjectCode(report.get("_project_id").textValue());
    projectDataTypeReport.setType(report.get("_type").textValue());
    projectDataTypeReport.setDonorCount(Long.parseLong(report.get("donor_id_count").textValue()));
    projectDataTypeReport.setSampleCount(Long.parseLong(report.get("analyzed_sample_id_count").textValue()));
    projectDataTypeReport.setSpecimenCount(Long.parseLong(report.get("specimen_id_count").textValue()));
    projectDataTypeReport.setObservationCount(Long.parseLong(report.get("analysis_observation_count").textValue()));
    return projectDataTypeReport;
  }

  private ProjectSequencingStrategyReport getExecutiveReport(JsonNode report, String releaseName) {
    val mapper = Jackson.DEFAULT;
    ProjectSequencingStrategyReport projectSequencingStrategyReport = new ProjectSequencingStrategyReport();
    projectSequencingStrategyReport.setReleaseName(releaseName);
    projectSequencingStrategyReport.setProjectCode(((ObjectNode) report).remove("_project_id").textValue());
    Map<String, Long> summary = mapper.convertValue(report, new TypeReference<Map<String, Long>>() {});
    projectSequencingStrategyReport.setCountSummary(summary);
    return projectSequencingStrategyReport;
  }

  public void generateReport(String releaseName) {
    generateReport(
        releaseName,
        releaseRepository.findProjectKeys(releaseName));
  }

  /**
   * TODO: see DCC-2445
   */
  public void generateReport(
      @NonNull final String releaseName,
      @NonNull final Set<String> projectKeys) {

    // TODO: check state: VALID or SIGNED_OFF only + no reports already

    val dictionaryNode = Jackson.DEFAULT.valueToTree(
        dictionaryRepository.findDictionaryByVersion(
            releaseRepository.findDictionaryVersion(releaseName)));
    val codeListsNode = Jackson.DEFAULT.valueToTree(
        codeListRepository.findCodeLists());

    generateReport(releaseName, projectKeys, dictionaryNode, codeListsNode);
  }

  /**
   * Generates reports in the background
   */
  public void generateReport(
      @NonNull final String releaseName,
      @NonNull final Set<String> projectKeys,
      @NonNull final JsonNode dictionaryNode,
      @NonNull final JsonNode codeListsNode) {

    val fileSystem = dccFileSystem.getFileSystem();
    val patterns = getPatterns(dictionaryNode);
    val mappings = getMapping(dictionaryNode, codeListsNode, SSM_M_TYPE,
        FieldNames.SubmissionFieldNames.SUBMISSION_OBSERVATION_SEQUENCING_STRATEGY);

    executor.execute(new Runnable() {

      @Override
      public void run() {
        log.info("Starting generating reports for '{}.{}'", releaseName, projectKeys);

        val outputDirPath = Reporter.process(
            releaseName,
            projectKeys,
            getReporterInput(
                fileSystem,
                projectKeys,
                getReleasePath(releaseName),
                patterns),
            mappings.get(),
            copyOf(hadoopProperties));
        log.info("Finished cascading process for report gathering of '{}.{}'", releaseName, projectKeys);

        for (val project : projectKeys) {
          ArrayNode projectReports = ReporterCollector.getJsonProjectDataTypeEntity(
              fileSystem, outputDirPath, releaseName, project);
          log.info("Persisting data type executive reports for '{}.{}': '{}'",
              new Object[] { releaseName, project, projectReports });

          for (val report : projectReports) {
            log.info("Persisting data type executive report for '{}.{}': '{}'",
                new Object[] { releaseName, project, report });
            projectDataTypeRepository.upsert(getProjectReport(report, releaseName));
          }

          ArrayNode sequencingStrategyReports = ReporterCollector.getJsonProjectSequencingStrategy(
              fileSystem, outputDirPath, releaseName, project, mappings.get());
          log.info("Persisting sequencing strategy executive reports for '{}.{}': '{}'",
              new Object[] { releaseName, project, sequencingStrategyReports });

          for (val report : sequencingStrategyReports) {
            log.info("Persisting sequencing strategy executive report for '{}.{}': '{}'",
                new Object[] { releaseName, project, report });
            projectSequencingStrategyRepository.upsert(getExecutiveReport(report, releaseName));
          }

        }

        log.info("Finished generating reports for '{}.{}'", releaseName, projectKeys);
      }

      private String getReleasePath(@NonNull final String releaseName) {
        return dccFileSystem.buildReleaseStringPath(releaseName);
      }

    });

  }

  private static ReporterInput getReporterInput(
      @NonNull final FileSystem fileSystem,
      @NonNull final Set<String> projectKeys,
      @NonNull final String releasePath,
      @NonNull final Map<FileType, String> patterns) {
    return ReporterInput.from(
        SubmissionInputData.getMatchingFiles(
            fileSystem, releasePath, projectKeys, patterns));
  }

}