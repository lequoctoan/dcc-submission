package org.icgc.dcc.web;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.icgc.dcc.release.ReleaseService;
import org.icgc.dcc.release.model.QRelease;
import org.icgc.dcc.release.model.Release;
import org.icgc.dcc.release.model.Submission;
import org.icgc.dcc.validation.report.FieldReport;
import org.icgc.dcc.validation.report.SchemaReport;
import org.icgc.dcc.validation.report.SubmissionReport;

import com.google.inject.Inject;

@Path("releases")
public class ReleaseResource {

  @Inject
  private ReleaseService releaseService;

  @GET
  public Response getResources() {
    List<Release> releases = releaseService.query().list();

    return Response.ok(releases).build();
  }

  @GET
  @Path("{name}")
  public Response getReleaseByName(@PathParam("name") String name) {
    Release release = releaseService.where(QRelease.release.name.eq(name)).singleResult();
    if(release == null) {
      return Response.status(Status.NOT_FOUND).entity(new ServerErrorResponseMessage("NoSuchRelease", name)).build();
    }
    return ResponseTimestamper.ok(release).build();
  }

  @PUT
  @Path("{name}")
  public Response updateRelease(@PathParam("name") String name, Release release, @Context Request req) {
    if(release != null) {
      ResponseTimestamper.evaluate(req, release);

      if(this.releaseService.list().isEmpty()) {
        this.releaseService.createInitialRelease(release);
      } else {
        // for now nothing is allowed to change
        /*
         * UpdateOperations<Release> ops =
         * this.releaseService.getDatastore().createUpdateOperations(Release.class).set("state", release.getState());
         * 
         * Query<Release> updateQuery =
         * this.releaseService.getDatastore().createQuery(Release.class).field("name").equal(name);
         * 
         * this.releaseService.getDatastore().update(updateQuery, ops);
         */
      }
      return ResponseTimestamper.ok(release).build();
    } else {
      return Response.status(Status.BAD_REQUEST).entity(new ServerErrorResponseMessage("ReleaseUpdateError")).build();
    }
  }

  /*
   * // web service for adding submission to release (testing use only)
   * 
   * @POST
   * 
   * @Consumes("application/json")
   * 
   * @Path("{name}") public Response addSubmission(@PathParam("name") String name, Submission submission) {
   * checkArgument(submission != null);
   * 
   * UpdateOperations<Release> ops =
   * this.releaseService.getDatastore().createUpdateOperations(Release.class).add("submissions", submission);
   * 
   * Query<Release> updateQuery =
   * this.releaseService.getDatastore().createQuery(Release.class).field("name").equal(name);
   * 
   * this.releaseService.getDatastore().update(updateQuery, ops);
   * 
   * return Response.ok(submission).build(); }
   */

  @GET
  @Path("{name}/submissions/{projectKey}")
  public Response getSubmission(@PathParam("name") String name, @PathParam("projectKey") String projectKey) {
    Submission submission = this.releaseService.getSubmission(name, projectKey);
    if(submission == null) {
      return Response.status(Status.NOT_FOUND)
          .entity(new ServerErrorResponseMessage("NoSuchSubmission", name, projectKey)).build();
    }
    return Response.ok(submission).build();
  }

  @GET
  @Path("{name}/submissions/{projectKey}/report")
  public Response getSubmissionReport(@PathParam("name") String name, @PathParam("projectKey") String projectKey) {
    Submission submission = this.releaseService.getSubmission(name, projectKey);
    if(submission == null) {
      return Response.status(Status.NOT_FOUND)
          .entity(new ServerErrorResponseMessage("NoSuchSubmission", name, projectKey)).build();
    }
    SubmissionReport report = submission.getReport();
    return Response.ok(report).build();
  }

  @GET
  @Path("{name}/submissions/{projectKey}/report/{schema}")
  public Response getSchemaReport(@PathParam("name") String name, @PathParam("projectKey") String projectKey,
      @PathParam("schema") String schema) {
    Submission submission = this.releaseService.getSubmission(name, projectKey);
    if(submission == null) {
      return Response.status(Status.NOT_FOUND)
          .entity(new ServerErrorResponseMessage("NoSuchSubmission", name, projectKey)).build();
    }
    SubmissionReport report = submission.getReport();
    if(report == null) {
      return Response.status(Status.NOT_FOUND).entity(new ServerErrorResponseMessage("NoSuchReport", name, projectKey))
          .build();
    }
    SchemaReport schemaReport = report.getSchemaReport(schema);
    if(schemaReport == null) {
      return Response.status(Status.NOT_FOUND).entity(new ServerErrorResponseMessage("NoSuchReportInSchema", schema))
          .build();
    }
    return Response.ok(schemaReport).build();
  }

  @GET
  @Path("{name}/submissions/{projectKey}/report/{schema}/{field}")
  public Response getFieldReport(@PathParam("name") String name, @PathParam("projectKey") String projectKey,
      @PathParam("schema") String schema, @PathParam("field") String field) {
    Submission submission = this.releaseService.getSubmission(name, projectKey);
    if(submission == null) {
      return Response.status(Status.NOT_FOUND)
          .entity(new ServerErrorResponseMessage("NoSuchSubmission", name, projectKey)).build();
    }
    SubmissionReport report = submission.getReport();
    if(report == null) {
      return Response.status(Status.NOT_FOUND).entity(new ServerErrorResponseMessage("NoSuchReport", name, projectKey))
          .build();
    }
    SchemaReport schemaReport = report.getSchemaReport(schema);
    if(schemaReport == null) {
      return Response.status(Status.NOT_FOUND).entity(new ServerErrorResponseMessage("NoSuchReportInSchema", schema))
          .build();
    }
    FieldReport fieldReport = schemaReport.getFieldReport(field);
    if(fieldReport == null) {
      return Response.status(Status.NOT_FOUND).entity(new ServerErrorResponseMessage("NoReportForField", field))
          .build();
    }
    return Response.ok(fieldReport).build();
  }
}
