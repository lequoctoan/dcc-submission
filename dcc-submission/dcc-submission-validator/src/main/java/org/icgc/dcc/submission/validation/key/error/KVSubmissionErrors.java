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
package org.icgc.dcc.submission.validation.key.error;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Maps.newLinkedHashMap;
import static org.icgc.dcc.submission.validation.key.core.KVDictionary.RELATIONS;
import static org.icgc.dcc.submission.validation.key.core.KVDictionary.getErrorFieldNames;
import static org.icgc.dcc.submission.validation.key.enumeration.KVErrorType.COMPLEX_SURJECTION;
import static org.icgc.dcc.submission.validation.key.enumeration.KVErrorType.PRIMARY_RELATION;
import static org.icgc.dcc.submission.validation.key.enumeration.KVErrorType.SECONDARY_RELATION;
import static org.icgc.dcc.submission.validation.key.enumeration.KVErrorType.SIMPLE_SURJECTION;
import static org.icgc.dcc.submission.validation.key.error.KVSubmissionErrors.KVReportError.kvReportError;
import static org.icgc.dcc.submission.validation.key.surjectivity.SurjectivityValidator.COMPLEX_SURJECTION_ERROR_LINE_NUMBER;
import static org.icgc.dcc.submission.validation.key.surjectivity.SurjectivityValidator.SIMPLE_SURJECTION_ERROR_LINE_NUMBER;

import java.util.List;
import java.util.Map;

import lombok.Value;
import lombok.val;
import lombok.experimental.Builder;
import lombok.extern.slf4j.Slf4j;

import org.codehaus.jackson.annotate.JsonProperty;
import org.icgc.dcc.submission.validation.core.ErrorType;
import org.icgc.dcc.submission.validation.key.core.KVDictionary;
import org.icgc.dcc.submission.validation.key.data.KVKeys;
import org.icgc.dcc.submission.validation.key.enumeration.KVErrorType;
import org.icgc.dcc.submission.validation.key.enumeration.KVFileType;
import org.icgc.dcc.submission.validation.key.report.KVReport;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * TODO: pretty inefficient
 */
@Slf4j
public class KVSubmissionErrors {

  // TODO: create proper data structure
  private final Map<KVFileType, Map<String, Map<Long, List<KVRowError>>>> errors = newLinkedHashMap();

  /**
   * TODO: create other wrappers like the surjection one
   */
  public void addError(KVFileType fileType, String fileName, long lineNumber, KVErrorType errorType, KVKeys keys) {
    log.debug("Reporting '{}' error for '{}.{}.{}': '{}'",
        new Object[] { errorType, fileType, fileName, lineNumber, keys });
    // TODO: address inefficiency
    getRowErrors(
        getFileErrors(
            getFileTypeErrors(errors, fileType),
            fileName),
        lineNumber)
        .add(new KVRowError(errorType, keys, lineNumber));
  }

  public void addSimpleSurjectionError(KVFileType fileType, String fileName, KVKeys keys) {
    addError(fileType, fileName, SIMPLE_SURJECTION_ERROR_LINE_NUMBER, SIMPLE_SURJECTION, keys);
  }

  public void addComplexSurjectionError(KVFileType fileType, String fileName, KVKeys keys) {
    addError(fileType, fileName, COMPLEX_SURJECTION_ERROR_LINE_NUMBER, COMPLEX_SURJECTION, keys);
  }

  public boolean reportSubmissionErrors(KVReport report) {
    boolean status = true;
    for (val submissionEntry : errors.entrySet()) {

      val fileType = submissionEntry.getKey();
      val fileTypeErrors = submissionEntry.getValue();
      log.info("Reporting file type errors for '{}' ('{}' errors)", fileType, fileTypeErrors.size());

      for (val fileTypeEntry : fileTypeErrors.entrySet()) {
        val fileName = fileTypeEntry.getKey();
        val fileErrors = fileTypeEntry.getValue();
        log.info("Reporting file errors for '{}' ('{}' errors)", fileName, fileErrors.size());

        for (val fileEntry : fileErrors.entrySet()) {
          val lineNumber = fileEntry.getKey();
          val rowErrors = fileEntry.getValue();
          log.debug("Reporting row errors for '{}' ('{}' errors)", lineNumber, rowErrors.size());

          for (val rowError : rowErrors) {
            val errorType = rowError.getErrorType();
            rowError.report(
                report,
                fileName,
                getErrorFieldNames(fileType, errorType),
                getErrorParams(fileType, errorType));
            status = false;
          }
        }
      }
    }
    return status;
  }

  private Object[] getErrorParams(KVFileType fileType, KVErrorType errorType) {
    Object[] errorParams = null;
    if (errorType == PRIMARY_RELATION || errorType == SECONDARY_RELATION) {
      val referencedFileType = RELATIONS.get(fileType);
      val referencedFields = KVDictionary.PKS.get(referencedFileType);
      errorParams = new Object[] { referencedFileType, referencedFields };
    } else if (errorType == SIMPLE_SURJECTION) {
      val referencingFileType = getReferencingFileType(fileType);
      val referencingFields = KVDictionary.PKS.get(referencingFileType);
      errorParams = new Object[] { referencingFileType, referencingFields };
    }
    return errorParams;
  }

  /**
   * Should NOT be called in the context of complex surjection as it can only return one such type.
   */
  private KVFileType getReferencingFileType(KVFileType fileType) {
    KVFileType referencingFileType = null;
    for (val entry : RELATIONS.entrySet()) {
      if (entry.getValue() == fileType) {
        checkState(referencingFileType == null, "TODO");
        return entry.getKey();
      }
    }
    return checkNotNull(referencingFileType, "TODO");
  }

  private Map<String, Map<Long, List<KVRowError>>> getFileTypeErrors(
      Map<KVFileType, Map<String, Map<Long, List<KVRowError>>>> submissionErrors,
      KVFileType fileType) {
    if (!submissionErrors.containsKey(fileType)) {
      submissionErrors.put(fileType, Maps.<String, Map<Long, List<KVRowError>>> newLinkedHashMap());
    }
    return submissionErrors.get(fileType);
  }

  private Map<Long, List<KVRowError>> getFileErrors(
      Map<String, Map<Long, List<KVRowError>>> fileTypeErrors,
      String fileName) {
    if (!fileTypeErrors.containsKey(fileName)) {
      fileTypeErrors.put(fileName, Maps.<Long, List<KVRowError>> newLinkedHashMap());
    }
    return fileTypeErrors.get(fileName);
  }

  private List<KVRowError> getRowErrors(
      Map<Long, List<KVRowError>> fileErrors,
      long lineNumber) {
    if (!fileErrors.containsKey(lineNumber)) {
      fileErrors.put(lineNumber, Lists.<KVRowError> newArrayList());
    }
    return fileErrors.get(lineNumber);
  }

  @Value
  public class KVRowError {

    private final KVErrorType errorType;
    private final KVKeys keys;
    private final long lineNumber;

    public void report(
        KVReport report,
        String fileName,
        List<String> fieldNames,
        Object[] errorParams
        ) {
      report.report(
          kvReportError()

              .fileName(fileName)
              .fieldNames(fieldNames)
              .params(errorParams)
              .type(errorType.getErrorType())
              .lineNumber(lineNumber)
              .value(keys.getValues())

              .build());
    }
  }

  @Value
  @Builder(builderMethodName = "kvReportError")
  public static class KVReportError {

    @JsonProperty
    String fileName;
    @JsonProperty
    List<String> fieldNames;
    @JsonProperty
    long lineNumber;
    @JsonProperty
    Object value;
    @JsonProperty
    ErrorType type;
    @JsonProperty
    Object[] params;

    private KVReportError(
        @JsonProperty("fileName") String fileName,
        @JsonProperty("fieldNames") List<String> fieldNames,
        @JsonProperty("lineNumber") long lineNumber,
        @JsonProperty("value") Object value,
        @JsonProperty("type") ErrorType type,
        @JsonProperty("params") Object[] params)
    {
      this.fileName = fileName;
      this.fieldNames = fieldNames;
      this.lineNumber = lineNumber;
      this.value = value;
      this.type = type;
      this.params = params;
    }

  }
}
