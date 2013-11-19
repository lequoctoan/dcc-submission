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
package org.icgc.dcc.submission.normalization.steps;

import static cascading.tuple.Fields.ARGS;
import static cascading.tuple.Fields.REPLACE;
import static com.google.common.base.Preconditions.checkState;
import static org.icgc.dcc.core.model.FieldNames.SubmissionFieldNames.SUBMISSION_OBSERVATION_MUTATED_FROM_ALLELE;
import static org.icgc.dcc.core.model.FieldNames.SubmissionFieldNames.SUBMISSION_OBSERVATION_REFERENCE_GENOME_ALLELE;
import static org.icgc.dcc.submission.normalization.NormalizationReport.NormalizationCounter.COUNT_INCREMENT;
import static org.icgc.dcc.submission.normalization.NormalizationReport.NormalizationCounter.MARKED_AS_CONTROLLED;
import static org.icgc.dcc.submission.normalization.steps.Masking.CONTROLLED;
import static org.icgc.dcc.submission.normalization.steps.Masking.NORMALIZER_MASKING_FIELD;
import static org.icgc.dcc.submission.normalization.steps.Masking.OPEN;
import lombok.RequiredArgsConstructor;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.submission.normalization.NormalizationContext;
import org.icgc.dcc.submission.normalization.NormalizationStep;

import cascading.flow.FlowProcess;
import cascading.operation.BaseOperation;
import cascading.operation.Function;
import cascading.operation.FunctionCall;
import cascading.pipe.Each;
import cascading.pipe.Pipe;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;

import com.google.common.annotations.VisibleForTesting;

/**
 * Steps in charge of marking sensitive observations and optionally creating a "masked" counterpart to them.
 * <p>
 * A sensitive observation is one for which the original allele in the mutation does not match that of the reference
 * genome allele at the same position.
 * <p>
 * Split in two: marking and masking
 */
@Slf4j
@RequiredArgsConstructor
public final class SensitiveRowMarking implements NormalizationStep {

  public static final String STEP_NAME = "mark";

  static final Fields REFERENCE_GENOME_ALLELE_FIELD = new Fields(SUBMISSION_OBSERVATION_REFERENCE_GENOME_ALLELE);
  static final Fields MUTATED_FROM_ALLELE_FIELD = new Fields(SUBMISSION_OBSERVATION_MUTATED_FROM_ALLELE);

  @Override
  public String shortName() {
    return STEP_NAME;
  }

  @Override
  public Pipe extend(Pipe pipe, NormalizationContext context) {
    // Mark rows that are sensitive
    Fields argumentSelector = REFERENCE_GENOME_ALLELE_FIELD.append(MUTATED_FROM_ALLELE_FIELD).append(
        NORMALIZER_MASKING_FIELD);
    return new Each(pipe, argumentSelector, new SensitiveRowMarker(), REPLACE);
  }

  /**
   * Marks tuples that are sensitives.
   * <p>
   * This expects the {@link Masking#NORMALIZER_MASKING_FIELD} to be present already (as {@link Masking#OPEN} for all
   * observations).
   */
  @VisibleForTesting
  static final class SensitiveRowMarker extends BaseOperation<Void> implements Function<Void> {

    @VisibleForTesting
    SensitiveRowMarker() {
      super(ARGS);
    }

    @Override
    public void operate(@SuppressWarnings("rawtypes") FlowProcess flowProcess, FunctionCall<Void> functionCall) {

      val entry = functionCall.getArguments();

      // Ensure expected state
      {
        val existingMasking = Masking.getMasking(entry.getString(Masking.NORMALIZER_MASKING_FIELD));
        checkState(existingMasking.isPresent() && existingMasking.get() == Masking.OPEN,
            "Masking flag is expected to have been set to '%s' already", OPEN);
      }

      val referenceGenomeAllele = entry.getString(REFERENCE_GENOME_ALLELE_FIELD);
      val mutatedFromAllele = entry.getString(MUTATED_FROM_ALLELE_FIELD);

      // Mark if applicable
      final Masking masking;
      if (isSensitive(referenceGenomeAllele, mutatedFromAllele)) {
        log.info("Marking sensitive row: '{}'", entry); // Should be rare enough
        masking = CONTROLLED;

        // Increment counter
        flowProcess.increment(MARKED_AS_CONTROLLED, COUNT_INCREMENT);
      } else {
        log.debug("Marking open-access row: '{}'", entry);
        masking = OPEN;
      }

      functionCall.getOutputCollector().add(
          new Tuple(referenceGenomeAllele, mutatedFromAllele, masking.getTupleValue()));
    }

    private boolean isSensitive(String referenceGenomeAllele, String mutatedFromAllele) {
      return !referenceGenomeAllele.equals(mutatedFromAllele);
    }
  }
}
