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
package org.icgc.dcc.generator.service;

import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.dictionary.model.FileSchema;
import org.icgc.dcc.generator.config.GeneratorConfig;
import org.icgc.dcc.generator.core.CoreFileGenerator;
import org.icgc.dcc.generator.core.DataGenerator;
import org.icgc.dcc.generator.core.MetaFileGenerator;
import org.icgc.dcc.generator.core.OptionalFileGenerator;
import org.icgc.dcc.generator.core.PrimaryFileGenerator;
import org.icgc.dcc.generator.core.SecondaryFileGenerator;
import org.icgc.dcc.generator.model.ExperimentalFile;
import org.icgc.dcc.generator.model.OptionalFile;
import org.icgc.dcc.generator.utils.ResourceWrapper;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

@Slf4j
public class GeneratorService {

  private static final String DONOR_SCHEMA_NAME = "donor";

  private static final String SAMPLE_SCHEMA_NAME = "sample";

  private static final String SPECIMEN_SCHEMA_NAME = "specimen";

  private static final String FILE_NAME_DIVIDER = "_";

  private static final String META_FILE_TYPE = "m";

  private static final String PRIMARY_FILE_TYPE = "p";

  private static final String EXPRESSION_PRIMARY_FILE_TYPE = "g";

  private static final String SECONDARY_FILE_TYPE = "s";

  @SneakyThrows
  public void generate(GeneratorConfig config, File dictionaryFile, File codeListFile) {
    // Retrieve all configuration parameters
    String outputDirectory = config.getOutputDirectory();
    Integer numberOfDonors = config.getNumberOfDonors();
    Integer numberOfSpecimensPerDonor = config.getNumberOfSpecimensPerDonor();
    Integer numberOfSamplesPerSpecimen = config.getNumberOfSamplesPerSpecimen();
    String leadJurisdiction = config.getLeadJurisdiction();
    String tumourType = config.getTumourType();
    String institution = config.getInstitution();
    String platform = config.getPlatform();
    Long seed = config.getSeed();
    List<OptionalFile> optionalFiles = config.getOptionalFiles();
    List<ExperimentalFile> experimentalFiles = config.getExperimentalFiles();

    log.info("Checking validity of parameters");
    checkParameters(outputDirectory, leadJurisdiction, tumourType, institution, platform);

    log.info("Initializing Dictionary and CodeList files");
    ResourceWrapper resourceWrapper = new ResourceWrapper(dictionaryFile, codeListFile);

    generateFiles(outputDirectory, resourceWrapper, numberOfDonors, numberOfSpecimensPerDonor,
        numberOfSamplesPerSpecimen, leadJurisdiction, tumourType, institution, platform, seed, optionalFiles,
        experimentalFiles);
  }

  public static void checkParameters(String outputDirectory, String leadJurisdiction, String tumourType,
      String institution, String platform) throws Exception {
    checkParameter(new File(outputDirectory).exists(), "The output directory specified does not exist",
        "output directory", outputDirectory);
    checkParameter(
        leadJurisdiction.length() == 2,
        "The lead jurisdiction parameter can only be of length 2. Please refer to http://dcc.icgc.org/pages/docs/ICGC_Data_Submission_Manual-0.6c-150512.pdf, for valid lead jurisdction values",
        "lead jurisdiction", leadJurisdiction);
    checkParameter(
        (Integer.parseInt(tumourType) >= 1 && Integer.parseInt(tumourType) <= 31 && tumourType.length() >= 2),
        "The tumour type parameter can only be a number between 1 and 31 (inclusive) and must be of length 2. Please refer to http://dcc.icgc.org/pages/docs/ICGC_Data_Submission_Manual-0.6c-150512.pdf, for valid lead jurisdction values",
        "tumour type", tumourType);
    checkParameter(
        (Integer.parseInt(institution) >= 1 && Integer.parseInt(institution) <= 98 && institution.length() == 3),
        "The institution parameter must be a number between 1 and 98 (inclusive) and must of length 3. Please refer to http://dcc.icgc.org/pages/docs/ICGC_Data_Submission_Manual-0.6c-150512.pdf, for valid lead jurisdction values",
        "institution", leadJurisdiction);
    checkParameter(
        (Integer.parseInt(platform) >= 1 && Integer.parseInt(platform) <= 75),
        "The platform parameter can only be of length 2. Please refer to http://dcc.icgc.org/pages/docs/ICGC_Data_Submission_Manual-0.6c-150512.pdf, for valid lead jurisdction values",
        "platform", platform);

  }

  public static void checkParameter(boolean expression, @Nullable String errorMessage,
      @Nullable Object... errorMessageArgs) throws Exception {
    if(expression == false) {
      throw new Exception(String.format("Invalid parameter {%2$s = %3$s}. %1$s", errorMessage, errorMessageArgs[0],
          errorMessageArgs[1]));
    }
  }

  @SneakyThrows
  private void generateFiles(String outputDirectory, ResourceWrapper resourceWrapper, Integer numberOfDonors,
      Integer numberOfSpecimensPerDonor, Integer numberOfSamplesPerSpecimen, String leadJurisdiction,
      String tumourType, String institution, String platform, Long seed, List<OptionalFile> optionalFiles,
      List<ExperimentalFile> experimentalFiles) throws JsonParseException, JsonMappingException, IOException {

    if(outputDirectory.charAt(outputDirectory.length() - 1) != '/') {
      outputDirectory = outputDirectory + "/";
    }

    DataGenerator datagen = new DataGenerator(seed);

    createCoreFiles(resourceWrapper, outputDirectory, numberOfDonors, numberOfSpecimensPerDonor,
        numberOfSamplesPerSpecimen, leadJurisdiction, tumourType, institution, platform, datagen);

    createOptionalFiles(resourceWrapper, outputDirectory, leadJurisdiction, tumourType, institution, platform,
        optionalFiles, datagen);

    createExperimentalFiles(resourceWrapper, outputDirectory, leadJurisdiction, tumourType, institution, platform,
        experimentalFiles, datagen);
  }

  private void createExperimentalFiles(ResourceWrapper resourceWrapper, String outputDirectory,
      String leadJurisdiction, String tumourType, String institution, String platform,
      List<ExperimentalFile> experimentalFiles, DataGenerator datagen) throws IOException {
    for(ExperimentalFile experimentalFile : experimentalFiles) {
      String fileType = experimentalFile.getFileType();
      String schemaName = experimentalFile.getName() + FILE_NAME_DIVIDER + fileType;
      Integer numberOfLines = experimentalFile.getNumberOfLinesPerForeignKey();

      datagen.buildPrimaryKey(resourceWrapper.getSchema(datagen, schemaName));

      if(fileType.equals(META_FILE_TYPE)) {
        createMetaFile(datagen, outputDirectory, resourceWrapper, schemaName, numberOfLines, leadJurisdiction,
            institution, tumourType, platform);
      } else if(fileType.equals(PRIMARY_FILE_TYPE)) {
        createPrimaryFile(datagen, outputDirectory, resourceWrapper, schemaName, numberOfLines, leadJurisdiction,
            institution, tumourType, platform);
      } else if(fileType.equals(EXPRESSION_PRIMARY_FILE_TYPE)) {
        createPrimaryFile(datagen, outputDirectory, resourceWrapper, schemaName, numberOfLines, leadJurisdiction,
            institution, tumourType, platform);
      } else if(fileType.equals(SECONDARY_FILE_TYPE)) {
        createSecondaryFile(datagen, outputDirectory, resourceWrapper, schemaName, numberOfLines, leadJurisdiction,
            institution, tumourType, platform);
      }
    }
  }

  private void createOptionalFiles(ResourceWrapper resourceWrapper, String outputDirectory, String leadJurisdiction,
      String tumourType, String institution, String platform, List<OptionalFile> optionalFiles, DataGenerator datagen)
      throws IOException {
    for(OptionalFile optionalFile : optionalFiles) {
      String schemaName = optionalFile.getName();
      Integer numberOfLinesPerDonor = optionalFile.getNumberOfLinesPerDonor();

      datagen.buildPrimaryKey(resourceWrapper.getSchema(datagen, schemaName));
      createOptionalFile(datagen, outputDirectory, resourceWrapper, schemaName, numberOfLinesPerDonor,
          leadJurisdiction, institution, tumourType, platform);
    }
  }

  private void createCoreFiles(ResourceWrapper resourceWrapper, String outputDirectory, Integer numberOfDonors,
      Integer numberOfSpecimensPerDonor, Integer numberOfSamplesPerSpecimen, String leadJurisdiction,
      String tumourType, String institution, String platform, DataGenerator datagen) throws IOException {
    createCoreFile(datagen, outputDirectory, resourceWrapper, DONOR_SCHEMA_NAME, numberOfDonors, leadJurisdiction,
        institution, tumourType, platform);
    createCoreFile(datagen, outputDirectory, resourceWrapper, SPECIMEN_SCHEMA_NAME, numberOfSpecimensPerDonor,
        leadJurisdiction, institution, tumourType, platform);
    createCoreFile(datagen, outputDirectory, resourceWrapper, SAMPLE_SCHEMA_NAME, numberOfSamplesPerSpecimen,
        leadJurisdiction, institution, tumourType, platform);
  }

  private static void createCoreFile(DataGenerator datagen, String outputDirectory, ResourceWrapper resourceWrapper,
      String schemaName, Integer linesPerForeignKey, String leadJurisdiction, String institution, String tumourType,
      String platform) throws IOException {
    log.info("Creating {} file", schemaName);
    datagen.buildPrimaryKey(resourceWrapper.getSchema(datagen, schemaName));
    FileSchema schema = resourceWrapper.getSchema(datagen, schemaName);
    CoreFileGenerator cfg = new CoreFileGenerator(datagen, outputDirectory);
    cfg.createFile(resourceWrapper, schema, linesPerForeignKey, leadJurisdiction, institution, tumourType, platform);
  }

  private static void createOptionalFile(DataGenerator datagen, String outputDirectory,
      ResourceWrapper resourceWrapper, String schemaName, Integer numberOfLinesPerDonor, String leadJurisdiction,
      String institution, String tumourType, String platform) throws IOException {
    log.info("Creating {} file", schemaName);
    FileSchema schema = resourceWrapper.getSchema(datagen, schemaName);
    OptionalFileGenerator ofg = new OptionalFileGenerator(datagen, outputDirectory);
    ofg.createFile(resourceWrapper, schema, numberOfLinesPerDonor, leadJurisdiction, institution, tumourType, platform);
  }

  private static void createMetaFile(DataGenerator datagen, String outputDirectory, ResourceWrapper resourceWrapper,
      String schemaName, Integer linesPerForeignKey, String leadJurisdiction, String institution, String tumourType,
      String platform) throws IOException {
    log.info("Creating {} file", schemaName);
    FileSchema schema = resourceWrapper.getSchema(datagen, schemaName);
    MetaFileGenerator mfg = new MetaFileGenerator(datagen, outputDirectory);
    mfg.createFile(resourceWrapper, schema, linesPerForeignKey, leadJurisdiction, institution, tumourType, platform);
  }

  private static void createPrimaryFile(DataGenerator datagen, String outputDirectory, ResourceWrapper resourceWrapper,
      String schemaName, Integer linesPerForeignKey, String leadJurisdiction, String institution, String tumourType,
      String platform) throws IOException {
    log.info("Creating {} file", schemaName);
    FileSchema schema = resourceWrapper.getSchema(datagen, schemaName);
    PrimaryFileGenerator pfg = new PrimaryFileGenerator(datagen, outputDirectory);
    pfg.createFile(resourceWrapper, schema, linesPerForeignKey, leadJurisdiction, institution, tumourType, platform);
  }

  private static void createSecondaryFile(DataGenerator datagen, String outputDirectory,
      ResourceWrapper resourceWrapper, String schemaName, Integer linesPerForeignKey, String leadJurisdiction,
      String institution, String tumourType, String platform) throws IOException {
    log.info("Creating {} file", schemaName);
    FileSchema schema = resourceWrapper.getSchema(datagen, schemaName);
    SecondaryFileGenerator sfg = new SecondaryFileGenerator(datagen, outputDirectory);
    sfg.createFile(resourceWrapper, schema, linesPerForeignKey, leadJurisdiction, institution, tumourType, platform);
  }
}
