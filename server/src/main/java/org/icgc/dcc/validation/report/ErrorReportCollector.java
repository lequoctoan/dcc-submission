/**
 * Copyright 2012(c) The Ontario Institute for Cancer Research. All rights reserved.
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
package org.icgc.dcc.validation.report;

import java.io.InputStream;
import java.util.List;

import org.codehaus.jackson.map.ObjectMapper;
import org.icgc.dcc.validation.CascadingStrategy;
import org.icgc.dcc.validation.FileSchemaFlowPlanner;
import org.icgc.dcc.validation.PlanExecutionException;

/**
 * 
 */
public class ErrorReportCollector implements ReportCollector {

  private final FileSchemaFlowPlanner planner;

  public ErrorReportCollector(FileSchemaFlowPlanner planner) {
    this.planner = planner;
  }

  @Override
  public Outcome collect(CascadingStrategy strategy, SchemaReport report) {
    try {
      InputStream src = strategy.readReportTap(this.planner, report.getName());
      ObjectMapper mapper = new ObjectMapper();
      List<FieldReport> fieldReports =
          mapper.readValue(src, mapper.getTypeFactory().constructCollectionType(List.class, FieldReport.class));
      report.getFieldReports().addAll(fieldReports);
      return fieldReports.isEmpty() ? Outcome.PASSED : Outcome.FAILED;
    } catch(Exception e) {
      throw new PlanExecutionException(e);
    }
  }

}
