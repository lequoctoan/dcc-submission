package org.icgc.dcc.validation;

import static com.google.common.base.Preconditions.checkArgument;

import org.icgc.dcc.config.ConfigModule;
import org.icgc.dcc.core.morphia.MorphiaModule;
import org.icgc.dcc.filesystem.FileSystemModule;
import org.icgc.dcc.release.CompletedRelease;
import org.icgc.dcc.release.NextRelease;
import org.icgc.dcc.release.ReleaseService;
import org.icgc.dcc.release.model.QueuedProject;
import org.icgc.dcc.release.model.Release;
import org.icgc.dcc.validation.service.ValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.typesafe.config.ConfigFactory;

public class Main {

  private static final Logger log = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) throws Exception {
    Injector injector = Guice.createInjector(new ConfigModule(ConfigFactory.load())//
        , new MorphiaModule()//
        , new FileSystemModule()//
        , new ValidationModule()//
        );

    final String releaseName = args[0];
    final String projectKey = args[1];
    checkArgument(releaseName != null);
    checkArgument(projectKey != null);
    log.info("releaseName = {} ", releaseName);
    log.info("projectKey = {} ", projectKey);

    ReleaseService releaseService = injector.getInstance(ReleaseService.class);
    Release release = getRelease(releaseService, releaseName);
    if(null != release) {
      ValidationService validationService = injector.getInstance(ValidationService.class);
      validationService.validate(release, new QueuedProject(projectKey, null));
    } else {
      log.info("there is no next release at the moment");
    }
  }

  private static Release getRelease(ReleaseService releaseService, final String releaseName) {
    NextRelease nextRelease = releaseService.getNextRelease();
    if(null != nextRelease) {
      Release release = nextRelease.getRelease();
      if(releaseName.equals(release.getName())) {
        return release;
      } else {
        Iterable<CompletedRelease> filter =
            Iterables.filter(releaseService.getCompletedReleases(), new Predicate<CompletedRelease>() {
              @Override
              public boolean apply(CompletedRelease input) {
                return releaseName.equals(input.getRelease().getName());
              }
            });
        return filter.iterator().hasNext() ? filter.iterator().next().getRelease() : null;
      }
    } else {
      return null;
    }
  }
}
