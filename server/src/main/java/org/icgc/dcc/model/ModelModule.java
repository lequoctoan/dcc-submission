package org.icgc.dcc.model;

import org.icgc.dcc.core.ProjectService;
import org.icgc.dcc.core.UserService;
import org.icgc.dcc.core.model.Project;
import org.icgc.dcc.core.model.User;
import org.icgc.dcc.dictionary.DictionaryService;
import org.icgc.dcc.dictionary.model.CodeList;
import org.icgc.dcc.dictionary.model.Dictionary;
import org.icgc.dcc.dictionary.visitor.DictionaryCloneVisitor;
import org.icgc.dcc.release.ReleaseService;
import org.icgc.dcc.release.model.Release;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.google.code.morphia.logging.MorphiaLoggerFactory;
import com.google.code.morphia.logging.slf4j.SLF4JLogrImplFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.mongodb.Mongo;
import com.mongodb.MongoURI;
import com.typesafe.config.Config;

/**
 * TODO: refactor into MorphiaModule, DictionaryModule and ReleaseModule
 */
@Deprecated
public class ModelModule extends AbstractModule {

  @Override
  protected void configure() {
    // Use SLF4J with Morphia
    MorphiaLoggerFactory.reset();
    MorphiaLoggerFactory.registerLogger(SLF4JLogrImplFactory.class);

    bind(Mongo.class).toProvider(new Provider<Mongo>() {

      @Inject
      private Config config;

      @Override
      public Mongo get() {
        try {
          return new MongoURI(config.getString("mongo.uri")).connect();
        } catch(Exception e) {
          throw new RuntimeException(e);
        }
      }
    }).in(Singleton.class);

    bind(Datastore.class).toProvider(new Provider<Datastore>() {

      @Inject
      Config config;

      @Inject
      Mongo mongo;

      @Inject
      Morphia morphia;

      @Override
      public Datastore get() {
        MongoURI uri = new MongoURI(config.getString("mongo.uri"));
        Datastore datastore = morphia.createDatastore(mongo, uri.getDatabase());
        datastore.ensureIndexes();
        return datastore;
      }
    }).in(Singleton.class);

    bindModelClasses(Project.class, Release.class, User.class, Dictionary.class, CodeList.class);
    bind(ProjectService.class);
    bind(ReleaseService.class);
    bind(UserService.class);
    bind(DictionaryCloneVisitor.class);// TODO: here?
    bind(DictionaryService.class);
  }

  private void bindModelClasses(final Class<?>... models) {
    Morphia morphia = new Morphia();
    for(Class<?> model : models) {
      morphia.map(model);
    }
    bind(Morphia.class).toInstance(morphia);
  }

}
