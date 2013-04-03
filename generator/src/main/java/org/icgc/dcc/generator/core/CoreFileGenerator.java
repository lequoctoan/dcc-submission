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
package org.icgc.dcc.generator.core;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;

import org.icgc.dcc.dictionary.model.Field;
import org.icgc.dcc.dictionary.model.FileSchema;
import org.icgc.dcc.dictionary.model.Relation;
import org.icgc.dcc.generator.model.PrimaryKey;
import org.icgc.dcc.generator.utils.ResourceWrapper;
import org.icgc.dcc.generator.utils.SubmissionFileUtils;

import com.google.common.base.Charsets;

@Slf4j
public class CoreFileGenerator {

  private static final String FIELD_SEPERATOR = "\t";

  private static final String LINE_SEPERATOR = "\n";

  private static final String DONOR_SCHEMA_NAME = "donor";

  private static final String SAMPLE_SCHEMA_NAME = "sample";

  private static final String TUMOUR_PRIMARY_KEY_FIELD_IDENTIFIER = "tumourSampleTypeID";

  private static final String CONTROL_PRIMARY_KEY_FIELD_IDENTIFIER = "controlledSampleTypeID";

  private static final String FILE_PATH_TOKEN_SEPERATOR = "/";

  // Essentially the primary key called analyzed_sample_id is not used
  private static final String SAMPLE_TYPE_FIELD_NAME = "analyzed_sample_id";

  private final DataGenerator datagen;

  private final String outputDirectory;

  public CoreFileGenerator(DataGenerator datagen, String outputDirectory) {
    this.datagen = datagen;
    this.outputDirectory = outputDirectory;
  }

  public void createFile(ResourceWrapper resourceWrapper, FileSchema schema, Integer linesPerForeignKey,
      String leadJurisdiction, String institution, String tumourType, String platform) throws IOException {

    File outputFile = generateFileName(datagen, schema, leadJurisdiction, institution, tumourType);
    @Cleanup
    Writer writer = buildFileWriter(outputFile);

    datagen.populateTermList(resourceWrapper, schema);

    log.info("Populating {} file", schema.getName());

    populateFile(resourceWrapper, schema, linesPerForeignKey, writer);
    datagen.resetUniqueValueFields();
    log.info("Finished populating {} file", schema.getName());
  }

  private void populateFileHeader(FileSchema schema, Writer writer) throws IOException {
    int counterForFieldNames = 1;
    int numberOfFields = schema.getFields().size();
    for(String fieldName : schema.getFieldNames()) {
      writeFieldValue(writer, counterForFieldNames, numberOfFields, fieldName);
      counterForFieldNames++;
    }
    writer.write(LINE_SEPERATOR);
  }

  private Writer buildFileWriter(File outputFile) throws FileNotFoundException {
    FileOutputStream fos = new FileOutputStream(outputFile);
    OutputStreamWriter osw = new OutputStreamWriter(fos, Charsets.UTF_8);
    return new BufferedWriter(osw);
  }

  private File generateFileName(DataGenerator datagen, FileSchema schema, String leadJurisdiction, String institution,
      String tumourType) throws IOException {
    List<String> fileNameTokens = newArrayList(leadJurisdiction, tumourType, institution, schema.getName());
    String fileName = SubmissionFileUtils.generateFileName(fileNameTokens);
    String pathName = outputDirectory + FILE_PATH_TOKEN_SEPERATOR + fileName;
    File outputFile = new File(pathName);
    checkArgument(outputFile.exists() == false, "A file with the name '%s' already exists.", fileName);
    outputFile.createNewFile();

    return outputFile;
  }

  private void populateFile(ResourceWrapper resourceWrapper, FileSchema schema, Integer linesPerForeignKey,
      Writer writer) throws IOException {
    populateFileHeader(schema, writer);
    String schemaName = schema.getName();
    List<Relation> schemaRelations = schema.getRelations();

    int lengthOfForeignKeys = calcuateLengthOfForeignKeys(schema, linesPerForeignKey, schemaRelations);
    int numberOfLinesPerForeignKey = calculateNumberOfLinesPerForeingKey(schema, linesPerForeignKey, schemaRelations);

    if(schemaName.equals(SAMPLE_SCHEMA_NAME)) {
      addSamplePrimaryKeys(schemaName);
    } else if(schemaName.equals("specimen")) {
      PrimaryKey pk = new PrimaryKey("donor", "donor_id_for_optional_file");
      datagen.getPrimaryKeys().add(pk);
    }

    for(int foreignKeyEntry = 0; foreignKeyEntry < lengthOfForeignKeys; foreignKeyEntry++) {
      for(int foreignKeyEntryLineNumber = 0; foreignKeyEntryLineNumber < numberOfLinesPerForeignKey; foreignKeyEntryLineNumber++) {
        int counterForFields = 1;
        int numberOfFields = schema.getFields().size();
        for(Field field : schema.getFields()) {
          String fieldValue = getFieldValue(resourceWrapper, schema, writer, schemaName, foreignKeyEntry, field);

          writeFieldValue(writer, counterForFields, numberOfFields, fieldValue);

          counterForFields++;
        }
        writer.write(LINE_SEPERATOR);
      }
      numberOfLinesPerForeignKey = calculateNumberOfLinesPerForeingKey(schema, linesPerForeignKey, schemaRelations);
    }
  }

  private void writeFieldValue(Writer writer, int counterForFields, int numberOfFields, String fieldValue)
      throws IOException {
    if(counterForFields == numberOfFields) {
      writer.write(fieldValue);
    } else {
      writer.write(fieldValue + FIELD_SEPERATOR);
    }
  }

  private String getFieldValue(ResourceWrapper resourceWrapper, FileSchema schema, Writer writer, String schemaName,
      int foreignKeyEntry, Field field) throws IOException {
    String fieldValue = null;
    String fieldName = field.getName();

    List<String> foreignKeys = datagen.getFileForeignKey(schema, fieldName);
    if(foreignKeys != null) {
      fieldValue = foreignKeys.get(foreignKeyEntry);
    }

    if(fieldValue == null) {
      fieldValue = datagen.getFieldValue(resourceWrapper, schemaName, field, schema.getUniqueFields());
    }

    if(resourceWrapper.isUniqueField(schema.getUniqueFields(), fieldName)) {
      datagen.getFilePrimaryKey(schemaName, fieldName).add(fieldValue);
    }

    // Special case for sample, to add whether sample type is controlled or tumour
    if(schema.getName().equals(SAMPLE_SCHEMA_NAME) && field.getName().equals(SAMPLE_TYPE_FIELD_NAME)) {
      addOutputToSamplePrimaryKeys(schema, field, fieldValue);
    } else if(schema.getName().equals("specimen") && field.getName().equals("donor_id")) {
      datagen.getFilePrimaryKey("donor", "donor_id_for_optional_file").add(fieldValue);
    }

    return fieldValue;
  }

  private void addOutputToSamplePrimaryKeys(FileSchema schema, Field field, String fieldValue) {
    int x = datagen.generateRandomInteger(0, 2);
    if(x == 0) {
      datagen.getFilePrimaryKey(SAMPLE_SCHEMA_NAME, TUMOUR_PRIMARY_KEY_FIELD_IDENTIFIER).add(fieldValue);
    } else {
      datagen.getFilePrimaryKey(SAMPLE_SCHEMA_NAME, CONTROL_PRIMARY_KEY_FIELD_IDENTIFIER).add(fieldValue);
    }
  }

  private void addSamplePrimaryKeys(String schemaName) {
    PrimaryKey tumourPrimaryKey = new PrimaryKey(SAMPLE_SCHEMA_NAME, TUMOUR_PRIMARY_KEY_FIELD_IDENTIFIER);
    datagen.getPrimaryKeys().add(tumourPrimaryKey);

    PrimaryKey controlPrimaryKey = new PrimaryKey(SAMPLE_SCHEMA_NAME, CONTROL_PRIMARY_KEY_FIELD_IDENTIFIER);
    datagen.getPrimaryKeys().add(controlPrimaryKey);
  }

  /**
   * Calculates the number Of non-repetitive entries (with regards to the foreign key fields) to be inserted in the file
   */
  private int calcuateLengthOfForeignKeys(FileSchema schema, Integer numberOfLinesPerPrimaryKey,
      List<Relation> relations) {
    int lengthOfForeingKeys;
    if(schema.getName().equals(DONOR_SCHEMA_NAME)) {
      lengthOfForeingKeys = numberOfLinesPerPrimaryKey;
    } else {
      Relation randomRelation = relations.get(0);
      String relatedFieldName = randomRelation.getFields().get(0);
      lengthOfForeingKeys = datagen.getFileForeignKey(schema, relatedFieldName).size();
    }
    return lengthOfForeingKeys;
  }

  /**
   * Calculates the number of times a file entry repeats with regards to the foreign key
   */
  private int calculateNumberOfLinesPerForeingKey(FileSchema schema, Integer linesPerForeignKey,
      List<Relation> relations) {
    if(schema.getName().equals(DONOR_SCHEMA_NAME)) {
      return 1;
    } else {
      Relation randomRelation = relations.get(0);// If one relation is bidirectional, assumption is they both are
      if(relations.size() > 0 && randomRelation.isBidirectional()) {
        return datagen.generateRandomInteger(1, linesPerForeignKey);
      } else {
        return datagen.generateRandomInteger(0, linesPerForeignKey);
      }
    }
  }

}
