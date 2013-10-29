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
package org.icgc.dcc.submission.validation.cascading;

import static com.google.common.base.Objects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;

import java.io.Serializable;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.icgc.dcc.submission.validation.core.ErrorParameterKey;
import org.icgc.dcc.submission.validation.core.ValidationErrorCode;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;

/**
 * Each {@code Tuple} should have one field that holds an instance of this class. It is used to track the state
 * (valid/invalid and corresponding reasons) of the whole {@code Tuple}.
 */

public class TupleState implements Serializable {

  private final long offset;

  private List<TupleError> errors;

  private boolean structurallyValid; // to save time on filtering

  private final Set<String> missingFieldNames = new HashSet<String>();

  public TupleState() {
    this(-1L);
  }

  public TupleState(long offset) {
    this.structurallyValid = true;
    this.offset = offset;
  }

  public void reportError(ValidationErrorCode code, List<String> columnNames, Object values, Object... params) {
    checkArgument(code != null);
    ensureErrors().add(new TupleError(code, columnNames, values, this.getOffset(), code.build(params)));
    structurallyValid = code.isStructural() == false;
  }

  public void reportError(ValidationErrorCode code, String columnName, Object value, Object... params) {
    checkArgument(code != null);
    List<String> columnNames = Lists.newArrayList(columnName);
    ensureErrors().add(new TupleError(code, columnNames, value, this.getOffset(), code.build(params)));
    structurallyValid = code.isStructural() == false;
  }

  public Iterable<TupleError> getErrors() {
    return ensureErrors();
  }

  @JsonIgnore
  public boolean isValid() {
    return errors == null || errors.size() == 0;
  }

  @JsonIgnore
  public boolean isInvalid() {
    return isValid() == false;
  }

  @JsonIgnore
  public boolean isStructurallyValid() {
    return structurallyValid;
  }

  public long getOffset() {
    return offset;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(TupleState.class).add(ValidationFields.OFFSET_FIELD_NAME, offset)
        .add("valid", isValid()).add("errors", errors).toString();
  }

  public void addMissingField(String fieldName) {
    this.missingFieldNames.add(fieldName);
  }

  @JsonIgnore
  public boolean isFieldMissing(String fieldName) {
    return this.missingFieldNames.contains(fieldName);
  }

  /**
   * Used to lazily instantiate the errors list. This method never returns {@code null}.
   */
  private List<TupleError> ensureErrors() {
    return errors == null ? (errors = Lists.newArrayListWithExpectedSize(3)) : errors;
  }

  /**
   * Holds an error. The {@code code} uniquely identifies the error (e.g.: range error) and the {@code parameters}
   * capture the error details (e.g.: the expected range; min and max).
   */
  public static final class TupleError implements Serializable {

    private final ValidationErrorCode code;

    private final List<String> columnNames;

    private final Object value;

    private final Long line;

    private final Map<ErrorParameterKey, Object> parameters;

    public TupleError() {
      this.code = null;
      this.columnNames = Lists.newArrayList();
      this.value = null;
      this.line = null;
      this.parameters = new LinkedHashMap<ErrorParameterKey, Object>();
    }

    private TupleError(ValidationErrorCode code, List<String> columnNames, Object value, Long line,
        Map<ErrorParameterKey, Object> parameters) {
      this.code = code;
      this.columnNames = columnNames;
      this.value = firstNonNull(value, "");
      this.line = line;
      this.parameters = parameters;
    }

    public ValidationErrorCode getCode() {
      return this.code;
    }

    public List<String> getColumnNames() {
      return this.columnNames;
    }

    public Object getValue() {
      return this.value;
    }

    public Long getLine() {
      return this.line;
    }

    public Map<ErrorParameterKey, Object> getParameters() {
      return this.parameters;
    }

    @JsonIgnore
    public String getMessage() {
      return code.format(getParameters());
    }

    @Override
    public String toString() {
      return Objects.toStringHelper(TupleError.class).add("code", code).add("parameters", parameters).toString();
    }

  }

}
