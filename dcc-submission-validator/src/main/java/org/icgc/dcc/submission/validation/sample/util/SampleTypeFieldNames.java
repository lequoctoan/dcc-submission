/*
 * Copyright (c) 2014 The Ontario Institute for Cancer Research. All rights reserved.                             
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
package org.icgc.dcc.submission.validation.sample.util;

import static lombok.AccessLevel.PRIVATE;
import lombok.NoArgsConstructor;

import org.icgc.dcc.submission.validation.sample.SampleTypeValidator;

/**
 * Dictionary defined field names required to implement the requirements of the {@link SampleTypeValidator}.
 */
@NoArgsConstructor(access = PRIVATE)
public final class SampleTypeFieldNames {

  /**
   * Clinical field name constants.
   */
  public static final String SPECIMEN_ID_FIELD_NAME = "specimen_id";
  public static final String SPECIMEN_TYPE_FIELD_NAME = "specimen_type";
  public static final String SAMPLE_ID_FIELD_NAME = "analyzed_sample_id";

  /**
   * Feature type field name constants.
   */
  public static final String ANALYZED_SAMPLE_ID_FIELD_NAME = "analyzed_sample_id";
  public static final String MATCHED_SAMPLE_ID_FIELD_NAME = "matched_sample_id";
  public static final String REFERENCE_SAMPLE_TYPE_FIELD_NAME = "reference_sample_type";

}