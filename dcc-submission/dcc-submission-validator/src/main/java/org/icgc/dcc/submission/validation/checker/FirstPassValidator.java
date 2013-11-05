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
package org.icgc.dcc.submission.validation.checker;

import static org.icgc.dcc.submission.validation.core.ErrorType.ErrorLevel.FILE_LEVEL;
import static org.icgc.dcc.submission.validation.core.ErrorType.ErrorLevel.ROW_LEVEL;

import javax.validation.constraints.NotNull;

import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.submission.validation.checker.FileChecker.FileCheckers;
import org.icgc.dcc.submission.validation.checker.RowChecker.RowCheckers;
import org.icgc.dcc.submission.validation.service.ValidationContext;
import org.icgc.dcc.submission.validation.service.ValidationExecutor;
import org.icgc.dcc.submission.validation.service.Validator;

import com.google.common.annotations.VisibleForTesting;

@Slf4j
@NoArgsConstructor
public class FirstPassValidator implements Validator {

  @NotNull
  private FileChecker fileChecker;
  @NotNull
  private RowChecker rowChecker;

  /**
   * For testing purposes only.
   */
  @VisibleForTesting
  FirstPassValidator(FileChecker fileChecker, RowChecker rowChecker) {
    this.fileChecker = fileChecker;
    this.rowChecker = rowChecker;
  }

  @Override
  public void validate(ValidationContext validationContext) {
    lazyLoadCheckers(validationContext);

    for (String filename : listRelevantFiles(validationContext)) {
      log.info("Validate '{}' level well-formedness for file: {}", FILE_LEVEL, filename);

      fileChecker.check(filename);
      verifyState();

      if (fileChecker.canContinue()) {
        log.info("Validating '{}' well-formedness for file: '{}'", ROW_LEVEL, filename);
        rowChecker.check(filename);
        verifyState();
      }
    }
  }

  private void lazyLoadCheckers(ValidationContext validationContext) {
    if (this.fileChecker == null) {
      this.fileChecker = FileCheckers.getDefaultFileChecker(validationContext);
    }
    if (this.rowChecker == null) {
      this.rowChecker = RowCheckers.getDefaultRowChecker(validationContext);
    }
  }

  private Iterable<String> listRelevantFiles(ValidationContext validationContext) {
    return validationContext
        .getSubmissionDirectory()
        .listFiles(
            validationContext
                .getDictionary()
                .getFilePatterns());
  }

  /**
   * Checks if the validation has been cancelled.
   * 
   * @throws InterruptedException when interrupted by the {@link ValidationExecutor}
   */
  @SneakyThrows
  private void verifyState() {
    val cancelled = Thread.currentThread().isInterrupted();
    if (cancelled) {
      throw new InterruptedException("First pass validation was interrupted");
    }
  }

}
