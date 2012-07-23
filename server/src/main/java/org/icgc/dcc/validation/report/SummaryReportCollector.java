package org.icgc.dcc.validation.report;

import java.io.InputStream;
import java.util.List;

import org.codehaus.jackson.map.ObjectMapper;
import org.icgc.dcc.validation.CascadingStrategy;
import org.icgc.dcc.validation.FileSchemaFlowPlanner;
import org.icgc.dcc.validation.PlanExecutionException;

public class SummaryReportCollector implements ReportCollector {

  private final FileSchemaFlowPlanner planner;

  public SummaryReportCollector(FileSchemaFlowPlanner planner) {
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
    } catch(Exception e) {
      throw new PlanExecutionException(e);
    }

    return Outcome.PASSED;
  }
}
