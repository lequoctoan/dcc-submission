/*
 * Copyright (c) 2015 The Ontario Institute for Cancer Research. All rights reserved.                             
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
package org.icgc.dcc.submission.validation.pcawg.core;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Stopwatch.createStarted;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.uniqueIndex;
import static com.google.common.collect.Sets.difference;
import static org.icgc.dcc.common.core.model.FieldNames.SubmissionFieldNames.SUBMISSION_ANALYZED_SAMPLE_ID;
import static org.icgc.dcc.common.core.model.FieldNames.SubmissionFieldNames.SUBMISSION_DONOR_ID;
import static org.icgc.dcc.common.core.model.SpecialValue.MISSING_CODES;
import static org.icgc.dcc.common.core.util.FormatUtils.formatCount;
import static org.icgc.dcc.submission.validation.pcawg.util.ClinicalFields.getDonorId;
import static org.icgc.dcc.submission.validation.pcawg.util.ClinicalFields.getSampleSampleId;
import static org.icgc.dcc.submission.validation.pcawg.util.PCAWGFields.isPCAWGSample;
import static org.icgc.dcc.submission.validation.util.Streams.filter;

import java.util.List;
import java.util.Set;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.common.core.model.FileTypes.FileType;
import org.icgc.dcc.submission.core.model.Record;
import org.icgc.dcc.submission.core.report.Error;
import org.icgc.dcc.submission.core.report.ErrorType;
import org.icgc.dcc.submission.validation.core.ReportContext;
import org.icgc.dcc.submission.validation.pcawg.external.TCGAClient;
import org.icgc.dcc.submission.validation.pcawg.util.ClinicalIndex;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

/**
 * Implementation of https://wiki.oicr.on.ca/display/DCCREVIEW/PCAWG+Clinical+Field+Requirements
 */
@Slf4j
@RequiredArgsConstructor
public class ClinicalRuleEngine {

  /**
   * Metadata.
   */
  private final List<ClinicalRule> rules;

  /**
   * Data.
   */
  @NonNull
  private final Clinical clinical;
  @NonNull
  private final ClinicalIndex index;
  private final boolean tcga;

  /**
   * Reference.
   */
  @NonNull
  private final TCGAClient tcgaClient;
  @NonNull
  private final Set<String> referenceSampleIds;

  /**
   * State.
   */
  @NonNull
  private final ReportContext context;
  private final List<FileType> numbers = newArrayList();

  public void execute() {
    val watch = createStarted();
    log.info("Starting {} clinical validation with {} reference sample ids...", tcga ? "TCGA" : "non-TCGA",
        formatCount(referenceSampleIds));

    log.info("Calculating PCAWG clinical...");
    val pcawgClinical = calculatePCAWGClinical();
    val pcawgIndex = new ClinicalIndex(pcawgClinical);
    log.info("Finished calculating PCAWG clinical");

    log.info("Validating sample study...");
    validateSamples(clinical.getCore().getSamples());
    log.info("Finished validating sample study");

    log.info("Validating core clinical...");
    validateCore(pcawgClinical.getCore());
    log.info("Finished validating core clinical");

    log.info("Validating optional clinical...");
    validateOptional(pcawgIndex.getDonorIds(), pcawgClinical.getOptional());
    log.info("Finished validating optional clinical");

    log.info("Finshed validating in {}", watch);
  }

  private Clinical calculatePCAWGClinical() {
    return new PCAWGClinicalCalculator(clinical, index).calculate();
  }

  private void validateSamples(List<Record> samples) {
    val canonicalSampleIds = Sets.newHashSet();

    for (val sample : samples) {
      val sampleId = getSampleSampleId(sample);
      val canonicalSampleId = getCanonicalSampleId(sample);

      // Record for downstream difference analysis
      canonicalSampleIds.add(canonicalSampleId);

      if (isPCAWGSample(sample)) {
        if (!isValidSampleId(canonicalSampleId)) {
          reportError(error(sample)
              .type(ErrorType.PCAWG_SAMPLE_STUDY_INVALID)
              .fieldNames(SUBMISSION_ANALYZED_SAMPLE_ID)
              .value(sampleId + (tcga ? " (" + canonicalSampleId + ")" : "")));
        }
      } else {
        if (isValidSampleId(canonicalSampleId)) {
          reportError(error(sample)
              .type(ErrorType.PCAWG_SAMPLE_STUDY_MISSING)
              .fieldNames(SUBMISSION_ANALYZED_SAMPLE_ID)
              .value(sampleId + (tcga ? " (" + canonicalSampleId + ")" : "")));
        }
      }
    }

    val missingSampleIds = difference(referenceSampleIds, canonicalSampleIds);
    if (!missingSampleIds.isEmpty()) {
      reportError(error(samples.get(0)) // Use first record to get a mandatory file name
          .lineNumber(-1)
          .type(ErrorType.PCAWG_SAMPLE_MISSING)
          .fieldNames(SUBMISSION_ANALYZED_SAMPLE_ID)
          .value(missingSampleIds));
    }
  }

  private void validateCore(ClinicalCore pcawgCore) {
    for (val pcawgCoreType : getValidatedCoreTypes()) {
      val records = pcawgCore.get(pcawgCoreType).get();
      validateFileTypeRules(records, pcawgCoreType);
    }
  }

  private void validateOptional(Set<String> pcawgDonorIds, ClinicalOptional pcawgOptional) {
    for (val pcawgOptionalType : getValidatedOptionalTypes()) {
      val records = pcawgOptional.get(pcawgOptionalType).get();

      validateFileTypeDonorPresence(records, pcawgOptionalType, pcawgDonorIds);
      validateFileTypeRules(records, pcawgOptionalType);
    }
  }

  private void validateFileTypeRules(List<Record> records, FileType fileType) {
    val rules = getFileTypeRules(fileType);

    // Execute all rules for each record
    for (val record : records) {
      for (val rule : rules) {
        if (!isRuleApplicable(rule)) {
          continue;
        }

        val fieldName = rule.getFieldName();
        val fieldValue = record.get(rule.getFieldName());

        val invalid = isFieldValueMissing(fieldValue);
        if (invalid) {
          reportError(error(record)
              .fieldNames(fieldName)
              .value(fieldValue)
              .type(ErrorType.PCAWG_CLINICAL_FIELD_REQUIRED));
        }
      }
    }
  }

  private void validateFileTypeDonorPresence(List<Record> records, FileType fileType, Set<String> pcawgDonorIds) {
    val recordMap = uniqueIndex(records, record -> getDonorId(record));
    for (val pcawgDonorId : pcawgDonorIds) {
      val invalid = !recordMap.containsKey(pcawgDonorId);
      if (invalid) {
        val donor = index.getDonor(pcawgDonorId);
        val number = getNumber(fileType);

        reportError(error(donor)
            .fieldNames(SUBMISSION_DONOR_ID)
            .type(ErrorType.PCAWG_CLINICAL_ROW_REQUIRED)
            .value(pcawgDonorId)
            .number(number)
            .params(fileType));
      }
    }
  }

  private Iterable<FileType> getValidatedCoreTypes() {
    // All:
    return ClinicalCore.getFileTypes();
  }

  private Iterable<FileType> getValidatedOptionalTypes() {
    // Subset!:
    return ImmutableList.of(FileType.FAMILY_TYPE, FileType.EXPOSURE_TYPE, FileType.THERAPY_TYPE);
  }

  private boolean isFieldValueMissing(String fieldValue) {
    val normalized = nullToEmpty(fieldValue).trim();

    return isNullOrEmpty(normalized) || MISSING_CODES.contains(normalized);
  }

  private boolean isRuleApplicable(ClinicalRule rule) {
    return !tcga || tcga && rule.isTcga();
  }

  private boolean isValidSampleId(String sampleId) {
    return referenceSampleIds.contains(sampleId);
  }

  private String getCanonicalSampleId(Record sample) {
    val sampleId = getSampleSampleId(sample);
    if (!tcga) {
      return sampleId;
    }

    // "Check" if this is already a UUID
    val uuid = sampleId.length() == 36;
    if (uuid) {
      return sampleId.toLowerCase();
    }

    // Special case for TCGA who submits barcodes to DCC and UUIDs to PanCancer
    val canonicalSampleId = nullToEmpty(sample.get("analyzed_sample_notes")).trim().toLowerCase();
    val barcode = canonicalSampleId.length() == 36;

    checkState(barcode, "Invalid TCGA UUID '%s' of length %s from barcode '%s'",
        canonicalSampleId, canonicalSampleId.length(), sampleId);

    return canonicalSampleId;
  }

  private List<ClinicalRule> getFileTypeRules(FileType fileType) {
    return filter(rules, rule -> fileType == rule.getFileType());
  }

  private Error.Builder error(Record record) {
    return Error.error()
        .fileName(record.getFile().getName())
        .lineNumber(record.getLineNumber());
  }

  private void reportError(Error.Builder builder) {
    context.reportError(builder.build());
  }

  private int getNumber(FileType fileType) {
    int number = numbers.indexOf(fileType);
    if (number == -1) {
      numbers.add(fileType);

      number = numbers.size() - 1;
    }

    return number;
  }

}