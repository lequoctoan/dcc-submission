package org.icgc.dcc.web;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;

import org.icgc.dcc.dictionary.DictionaryService;
import org.icgc.dcc.dictionary.model.Dictionary;
import org.icgc.dcc.dictionary.model.DictionaryState;

import com.google.inject.Inject;

@Path("dictionaries")
public class DictionaryResource {
  @Inject
  private DictionaryService dictionaries;

  @POST
  public Response addDictionary(Dictionary d) {
    checkArgument(d != null);
    if(this.dictionaries.list().isEmpty() == false) {
      return Response.status(Status.BAD_REQUEST).entity(new ServerErrorResponseMessage("NotInitialDictionary")).build();
    }
    this.dictionaries.add(d);

    return Response.created(UriBuilder.fromResource(DictionaryResource.class).path(d.getVersion()).build()).build();
  }

  @GET
  public Response getDictionaries() {
    List<Dictionary> dictionaries = this.dictionaries.list();
    if(dictionaries == null) {
      return Response.status(Status.NOT_FOUND).entity(new ServerErrorResponseMessage("NoDictionaries")).build();
    }
    return Response.ok(dictionaries).build();
  }

  @GET
  @Path("{version}")
  public Response getDictionary(@PathParam("version") String version) {
    Dictionary d = this.dictionaries.getFromVersion(version);
    if(d == null) {
      return Response.status(Status.NOT_FOUND).entity(new ServerErrorResponseMessage("NoSuchVersion", version)).build();
    }
    return ResponseTimestamper.ok(d).build();
  }

  @PUT
  @Path("{version}")
  public Response updateDictionary(@PathParam("version") String version, Dictionary newDictionary, @Context Request req) {
    Dictionary oldDictionary = this.dictionaries.getFromVersion(version);
    if(oldDictionary == null) {
      return Response.status(Status.NOT_FOUND).entity(new ServerErrorResponseMessage("NoSuchVersion", version)).build();
    } else if(oldDictionary.getState() != DictionaryState.OPENED) {
      return Response.status(Status.BAD_REQUEST).entity(new ServerErrorResponseMessage("DictionaryNotOpen", version))
          .build();
    } else if(newDictionary.getVersion().equals(version) == false) {
      return Response.status(Status.BAD_REQUEST)
          .entity(new ServerErrorResponseMessage("DictionaryVersionMismatch", version, newDictionary.getVersion()))
          .build();
    }
    ResponseTimestamper.evaluate(req, oldDictionary);
    this.dictionaries.update(newDictionary);

    return ResponseTimestamper.ok(newDictionary).build();
  }
}
