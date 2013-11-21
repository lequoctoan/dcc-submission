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
package org.icgc.dcc.submission.validation.primary.core;

import static org.icgc.dcc.submission.validation.platform.PlatformStrategy.FILE_NAME_SEPARATOR;

import java.io.Serializable;

import lombok.NonNull;
import lombok.Value;

import org.icgc.dcc.submission.dictionary.model.FileSchema;

import com.google.common.base.Joiner;

/**
 * Holds a reference to trimmed content. Used to plan outputs from the internal flow and inputs for the external flow.
 */
@Value
public class Key implements Serializable {

  /**
   * Constants.
   */
  private static final char FIELD_SEPARATOR = '-';
  private static final Joiner JOINER = Joiner.on(FIELD_SEPARATOR);

  @NonNull
  private final FileSchema schema;
  @NonNull
  private final String[] fields;

  public Key(FileSchema schema, String... fields) {
    this.schema = schema;
    this.fields = fields;
  }

  public String getName() {
    return schema.getName() + FILE_NAME_SEPARATOR + JOINER.join(fields);
  }

  @Override
  public String toString() {
    return getName();
  }

}