package org.icgc.dcc.validation;

import java.io.File;
import java.io.IOException;

import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.icgc.dcc.model.dictionary.Dictionary;
import org.icgc.dcc.validation.plan.Planner;

import cascading.cascade.Cascade;
import cascading.flow.Flow;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class Main {

  private final File root;

  private final File output;

  private final Dictionary dictionary;

  public Main(String[] args) throws JsonProcessingException, IOException {
    this.root = new File(args[0]);
    this.output = new File(args[1]);
    this.output.mkdirs();
    this.dictionary =
        new ObjectMapper().reader(Dictionary.class).readValue(
            Resources.toString(Main.class.getResource("/dictionary.json"), Charsets.UTF_8));
  }

  public static void main(String[] args) throws JsonProcessingException, IOException {
    new Main(args).doit();
  }

  private void doit() {
    if(output.exists() && output.listFiles() != null) {
      for(File f : output.listFiles()) {
        if(f.isFile()) {
          f.delete();
        }
      }
    }

    Injector injector = Guice.createInjector(new ValidationModule(root, output));

    Planner planner = injector.getInstance(Planner.class);
    Cascade c = planner.plan(new LocalFileSchemaDirectory(root), dictionary);
    c.writeDOT(new File(output, "cascade.dot").getAbsolutePath());
    for(Flow flow : c.getFlows()) {
      flow.writeDOT(new File(output, flow.getName() + ".dot").getAbsolutePath());
    }
    if(c.getFlows().size() > 0) {
      c.start();
    }
  }
}
