/*
 * Copyright (c) 2016 The Ontario Institute for Cancer Research. All rights reserved.                             
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
package org.icgc.dcc.submission.core.parser;

import static lombok.AccessLevel.PRIVATE;

import java.util.Map;

import lombok.NoArgsConstructor;

import org.apache.hadoop.fs.FileSystem;
import org.icgc.dcc.common.hadoop.parser.FileParser;
import org.icgc.dcc.common.hadoop.parser.FileParsers;
import org.icgc.dcc.submission.dictionary.model.FileSchema;

@NoArgsConstructor(access = PRIVATE)
public class SubmissionFileParsers {

  public static FileParser<Map<String, String>> newMapFileParser(FileSchema fileSchema) {
    return newMapFileParser(FileParsers.DEFAULT_FILE_SYSTEM, fileSchema);
  }

  public static FileParser<Map<String, String>> newMapFileParser(FileSystem fileSystem, FileSchema fileSchema) {
    return newMapFileParser(fileSystem, fileSchema, false);
  }

  public static FileParser<Map<String, String>> newMapFileParser(FileSystem fileSystem, FileSchema fileSchema,
      boolean processHeader) {
    return new FileParser<Map<String, String>>(fileSystem, new FileLineMapParser(fileSchema), processHeader);
  }

}
