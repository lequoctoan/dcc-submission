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
package org.icgc.dcc.submission.validation.primary;

import static com.google.common.collect.Iterables.size;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.val;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.submission.release.model.QueuedProject;
import org.icgc.dcc.submission.validation.core.Plan;
import org.icgc.dcc.submission.validation.planner.Planner;
import org.icgc.dcc.submission.validation.service.ValidationContext;
import org.icgc.dcc.submission.validation.service.Validator;

import com.google.inject.Inject;

/**
 * {@code Validator} implementation that performs relation, restriction and data type validations using Cascading as the
 * execution platform.
 * 
 * @see https://groups.google.com/d/msg/cascading-user/gjxB2Bg-56w/R1h5lhn-g2IJ
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @_(@Inject))
public class PrimaryValidator implements Validator {

  @NonNull
  private final Planner planner;

  @Override
  @SneakyThrows
  public void validate(ValidationContext context) {
    log.info("Preparing cascade for project '{}'", context.getProjectKey());

    // Shorthands
    val projectKey = context.getProjectKey();
    val dictionary = context.getDictionary();
    val submissionDirectory = context.getSubmissionDirectory();
    val queuedProject = new QueuedProject(context.getProjectKey(), context.getEmails());
    val platformStrategy = context.getPlatformStrategy();

    log.info("Planning cascade for project {}", projectKey);
    Plan plan = planner.plan(queuedProject, submissionDirectory, platformStrategy, dictionary);

    log.info("Planned cascade for project {}", projectKey);
    log.info("# internal flows: {}", size(plan.getInternalFlows()));
    log.info("# external flows: {}", size(plan.getExternalFlows()));

    log.info("Connecting cascade for project {}", projectKey);
    plan.connect(platformStrategy);
    log.info("Connected cascade for project {}", projectKey);

    try {
      log.info("Starting cascade for project {}", projectKey);
      plan.getCascade().complete();
      log.info("Finished cascade for project {}", projectKey);

      log.info("Collecting report for project {}", projectKey);
      plan.collect(context.getSubmissionReport());
      log.info("Finished collecting report for project {}", projectKey);
    } catch (Throwable t) {
      log.info("Exception completing cascade for project {}", projectKey, t);

      log.info("Stopping cascade for project {}", projectKey);
      plan.getCascade().stop();
      log.info("Stopped cascade for project {}", projectKey);

      // Rethrow for {@link Validator}
      throw t;
    }
  }

}
