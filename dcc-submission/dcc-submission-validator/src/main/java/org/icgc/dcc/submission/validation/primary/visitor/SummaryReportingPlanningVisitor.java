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
package org.icgc.dcc.submission.validation.primary.visitor;

import static com.google.common.collect.Lists.newArrayList;
import static org.icgc.dcc.submission.validation.primary.core.FlowType.INTERNAL;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.NonNull;
import lombok.val;

import org.icgc.dcc.submission.dictionary.model.Field;
import org.icgc.dcc.submission.dictionary.model.FileSchema;
import org.icgc.dcc.submission.dictionary.model.SummaryType;
import org.icgc.dcc.submission.validation.platform.PlatformStrategy;
import org.icgc.dcc.submission.validation.primary.core.FlowType;
import org.icgc.dcc.submission.validation.primary.report.FrequencyPlanElement;
import org.icgc.dcc.submission.validation.primary.report.SummaryPlanElement;
import org.icgc.dcc.submission.validation.primary.report.UniqueCountPlanElement;

public class SummaryReportingPlanningVisitor extends ReportingPlanningVisitor {

  public SummaryReportingPlanningVisitor(@NonNull PlatformStrategy platform) {
    super(platform, INTERNAL);
  }

  @Override
  public void visit(FileSchema fileSchema) {
    super.visit(fileSchema);
    for (val fileName : listMatchingFiles(fileSchema.getPattern())) {
      collectElements(
          fileSchema,
          fileName,
          buildSummaryTypeToFields(fileSchema));
    }
  }

  /**
   * Builds a map that associates each {@code SummaryType} with a list of corresponding {@code Field} from the
   * {@code FileSchema}
   */
  private Map<SummaryType, List<Field>> buildSummaryTypeToFields(FileSchema fileSchema) {
    Map<SummaryType, List<Field>> summaryTypeToFields = new LinkedHashMap<SummaryType, List<Field>>();
    for (val field : fileSchema.getFields()) {
      val summaryType = field.getSummaryType();
      List<Field> list = summaryTypeToFields.get(summaryType);
      if (list == null) {
        list = newArrayList();
        summaryTypeToFields.put(summaryType, list);
      }

      list.add(field);
    }

    return summaryTypeToFields;
  }

  /**
   * Collects element based on the {@code Field}'s {@code SummaryType}, so they can later be applied
   */
  private void collectElements(FileSchema fileSchema, String fileName, Map<SummaryType, List<Field>> summaryTypeToFields) {
    for (val summaryType : summaryTypeToFields.keySet()) {
      List<Field> fields = summaryTypeToFields.get(summaryType);
      FlowType flowType = getFlowType();

      if (summaryType == null) {
        collectReportingPlanElement(new SummaryPlanElement.CompletenessPlanElement(fileSchema, fileName, fields,
            flowType));
        continue;
      }

      switch (summaryType) {
      case AVERAGE:
        collectReportingPlanElement(new SummaryPlanElement.AveragePlanElement(fileSchema, fileName, fields, flowType));
        break;
      case MIN_MAX:
        collectReportingPlanElement(new SummaryPlanElement.MinMaxPlanElement(fileSchema, fileName, fields, flowType));
        break;
      case FREQUENCY:
        collectReportingPlanElement(new FrequencyPlanElement(fileSchema, fileName, fields, flowType));
        break;
      case UNIQUE_COUNT:
        collectReportingPlanElement(new UniqueCountPlanElement(fileSchema, fileName, fields, flowType));
        break;
      default:
        collectReportingPlanElement(new SummaryPlanElement.CompletenessPlanElement(fileSchema, fileName, fields,
            flowType));
        break;
      }
    }
  }

}
