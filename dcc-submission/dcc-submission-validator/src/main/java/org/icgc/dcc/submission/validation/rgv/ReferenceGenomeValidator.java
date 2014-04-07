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
package org.icgc.dcc.submission.validation.rgv;

import static com.google.common.collect.ImmutableList.of;
import static org.icgc.dcc.core.model.FieldNames.SubmissionFieldNames.SUBMISSION_OBSERVATION_REFERENCE_GENOME_ALLELE;
import static org.icgc.dcc.core.model.FileTypes.FileType.SGV_P_TYPE;
import static org.icgc.dcc.core.model.FileTypes.FileType.SSM_P_TYPE;
import static org.icgc.dcc.core.util.FormatUtils._;
import static org.icgc.dcc.submission.core.parser.FileParsers.newMapFileParser;
import static org.icgc.dcc.submission.core.report.Error.error;
import static org.icgc.dcc.submission.core.report.ErrorType.REFERENCE_GENOME_INSERTION_ERROR;
import static org.icgc.dcc.submission.core.report.ErrorType.REFERENCE_GENOME_MISMATCH_ERROR;
import static org.icgc.dcc.submission.validation.core.Validators.checkInterrupted;
import static org.icgc.dcc.submission.validation.rgv.util.ChromosomeConverter.convert;
import static org.icgc.dcc.submission.validation.rgv.util.ReferenceUtils.REFERENCE_INSERTION_VALUE;
import static org.icgc.dcc.submission.validation.rgv.util.ReferenceUtils.isInsertionType;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import lombok.Cleanup;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.apache.hadoop.fs.Path;
import org.icgc.dcc.core.model.DataType;
import org.icgc.dcc.core.model.FileTypes.FileType;
import org.icgc.dcc.submission.core.parser.FileParser;
import org.icgc.dcc.submission.core.parser.FileRecordProcessor;
import org.icgc.dcc.submission.validation.cascading.TupleState;
import org.icgc.dcc.submission.validation.core.ValidationContext;
import org.icgc.dcc.submission.validation.core.Validator;
import org.icgc.dcc.submission.validation.rgv.core.PrimaryFieldResolver;
import org.icgc.dcc.submission.validation.rgv.core.ReferenceGenomeFileType;
import org.icgc.dcc.submission.validation.rgv.reference.ReferenceGenome;
import org.icgc.dcc.submission.validation.rgv.report.TupleStateWriter;
import org.icgc.dcc.submission.validation.rgv.resolver.SgvPrimaryFieldResolver;
import org.icgc.dcc.submission.validation.rgv.resolver.SsmPrimaryFieldResolver;

/**
 * Support querying a reference genome data file in the form for chromosome-start-end to validate submission input.
 * <p>
 * This uses the picard utilities to query an indexed FASTA file, as a bench mark reference we can check roughly
 * 3,000,000 reference genomes in 200 seconds.
 * 
 * @see https://wiki.oicr.on.ca/display/DCCSOFT/Unify+genome+assembly+build+throughout+the+system
 * @see https://wiki.oicr.on.ca/display/DCCSOFT/SSM+data+model+supporting+controlled+fields+and+other+improvements#
 * SSMdatamodelsupportingcontrolledfieldsandotherimprovements-SSMvalidationinReferenceGenomesequenceValidationRGV
 */
@Slf4j
@RequiredArgsConstructor
public class ReferenceGenomeValidator implements Validator {

  /**
   * File types that are the target of validation.
   */
  private static final List<ReferenceGenomeFileType> REFERENCE_GENOME_FILE_TYPES = of(
      new ReferenceGenomeFileType(SSM_P_TYPE, new SsmPrimaryFieldResolver()),
      new ReferenceGenomeFileType(SGV_P_TYPE, new SgvPrimaryFieldResolver()));

  /**
   * The reference genome used to validate.
   */
  @NonNull
  private final ReferenceGenome reference;

  @Override
  public String getName() {
    return "Reference Genome Validator";
  }

  /**
   * Validate genome reference aligns with reference genome of submitted primary file. We assume at this stage the file
   * is well-formed, and that each individual field is sane.
   */
  @Override
  public void validate(ValidationContext context) {
    log.info("Starting...");

    // Selective validation filtering
    if (!isValidatable(context.getDataTypes())) {
      log.info("SSM / SGV validation not required for '{}'. Skipping...", context.getProjectKey());

      return;
    }

    validateFileTypes(context);
  }

  private void validateFileTypes(ValidationContext context) {
    for (val referringFileType : REFERENCE_GENOME_FILE_TYPES) {
      val fileType = referringFileType.getType();
      val fieldResolver = referringFileType.getFieldResolver();
      val files = context.getFiles(fileType);
      val skip = files.isEmpty();
      if (skip) {
        log.info("No '{}' file(s) for '{}'. Skipping...", fileType, context.getProjectKey());

        continue;
      }

      validateFileType(context, fileType, files, fieldResolver);
    }
  }

  @SneakyThrows
  private void validateFileType(ValidationContext context, FileType fileType, List<Path> files,
      PrimaryFieldResolver fieldResolver) {
    val fileParser = newMapFileParser(context.getFileSystem(), context.getFileSchema(fileType));
    for (val file : files) {
      @Cleanup
      val writer = createTupleStateWriter(context, file);

      // Get to work
      log.info("Performing reference genome validation on file '{}' for '{}'", file, context.getProjectKey());
      validateFile(context, file, fileParser, fieldResolver, writer);
      log.info("Finished performing reference genome validation for '{}'", context.getProjectKey());
    }
  }

  @SneakyThrows
  private void validateFile(final ValidationContext context, final Path filePath,
      final FileParser<Map<String, String>> fileParser, final PrimaryFieldResolver fieldResolver,
      final TupleStateWriter writer) {
    val fileName = filePath.getName();

    fileParser.parse(filePath, new FileRecordProcessor<Map<String, String>>() {

      @Override
      public void process(long lineNumber, Map<String, String> record) throws IOException {
        // Resolve fields
        val mutationType = fieldResolver.resolveMutationType(record);
        val chromosomeCode = fieldResolver.resolveChromosomeCode(record);
        val start = fieldResolver.resolveStart(record);
        val end = fieldResolver.resolveEnd(record);
        val actualReference = fieldResolver.resolveReferenceAllele(record);

        if (isInsertionType(mutationType)) {
          // Insertion
          val mismatch = !actualReference.equals(REFERENCE_INSERTION_VALUE);
          if (mismatch) {
            val type = REFERENCE_GENOME_INSERTION_ERROR;
            val value = formatValue(REFERENCE_INSERTION_VALUE, actualReference);
            val columnName = SUBMISSION_OBSERVATION_REFERENCE_GENOME_ALLELE;
            val param = reference.getVersion();

            // Database
            context.reportError(
                error()
                    .fileName(fileName)
                    .fieldNames(columnName)
                    .type(type)
                    .lineNumber(lineNumber)
                    .value(value)
                    .params(param)
                    .build());

            // File
            val tupleState = new TupleState(lineNumber);
            tupleState.reportError(type, columnName, value, param);
            writer.write(tupleState);
          }
        } else {
          // Deletion or substitution
          val chromosome = convert(chromosomeCode);
          val expectedReference = reference.getSequence(chromosome, start, end);

          val mismatch = !isMatch(actualReference, expectedReference);
          if (mismatch) {
            val type = REFERENCE_GENOME_MISMATCH_ERROR;
            val value = formatValue(expectedReference, actualReference);
            val columnName = SUBMISSION_OBSERVATION_REFERENCE_GENOME_ALLELE;
            val param = reference.getVersion();

            // Database
            context.reportError(
                error()
                    .fileName(fileName)
                    .fieldNames(columnName)
                    .type(type)
                    .lineNumber(lineNumber)
                    .value(value)
                    .params(param)
                    .build());

            // File
            val tupleState = new TupleState(lineNumber);
            tupleState.reportError(type, columnName, value, param);
            writer.write(tupleState);
          }
        }

        // Cooperate
        checkInterrupted(getName());
      }

    });
  }

  private static boolean isValidatable(Iterable<DataType> dataTypes) {
    for (val dataType : dataTypes) {
      for (val resolver : REFERENCE_GENOME_FILE_TYPES) {
        if (resolver.getType().getDataType() == dataType) {
          return true;
        }
      }
    }

    return false;
  }

  private static boolean isMatch(String controlAllele, String refSequence) {
    return controlAllele.equalsIgnoreCase(refSequence);
  }

  private static String formatValue(String expected, String actual) {
    return _("Expected: %s, Actual: %s", expected, actual);
  }

  private static TupleStateWriter createTupleStateWriter(ValidationContext context, Path file) throws IOException {
    return new TupleStateWriter(
        context.getFileSystem(), new Path(context.getSubmissionDirectory().getValidationDirPath()), file);
  }

}
