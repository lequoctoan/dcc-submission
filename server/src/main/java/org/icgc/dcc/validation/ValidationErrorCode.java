package org.icgc.dcc.validation;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.icgc.dcc.dictionary.model.ValueType;
import org.icgc.dcc.validation.cascading.TupleState.TupleError;

import com.google.common.collect.ImmutableMap;

public enum ValidationErrorCode { // TODO: DCC-505 to fix the message (currently not used for anything)

  /**
   * Number of columns does not match that of header.
   */
  STRUCTURALLY_INVALID_ROW_ERROR("structurally invalid row: %s columns against %s declared in the header (row will be ignored by the rest of validation)", true) {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      checkArgument(params != null);
      checkArgument(params.length == 1);
      checkArgument(params[0] instanceof Integer);
      return ImmutableMap.of(EXPECTED_VALUE, params[0]);
    }
  }, //
  /**
   * No matching value(s) for referencED field(s).
   * <p>
   * Example: for specimen.donor_id and a value "A", if donor.donor_id does not contain "A"
   * <p>
   * Not to be confused with its file counterpart {@code RELATION_FILE_ERROR}
   */
  RELATION_VALUE_ERROR("invalid value(s) (%s) for field(s) %s.%s. Expected to match value(s) in: %s.%s") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      checkArgument(params != null);
      checkArgument(params.length == 2);
      checkArgument(params[0] instanceof String);
      checkArgument(params[1] instanceof List);
      return ImmutableMap.of(RELATION_SCHEMA, params[0], RELATION_COLUMNS, params[1]);
    }
  }, //
  /**
   * No matching value(s) for referencING field(s) (only applicable if relation is set to bidirectional).
   * <p>
   * Example: for specimen.specimen_id and a value "B", if sample.specimen_id does not contain "B" (but not if
   * surgery.specimen_id is missing that value, as the relation from surgery is not bidirectional)
   * <p>
   * Not quite the value-counterpart to {@code REVERSE_RELATION_FILE_ERROR}
   */
  RELATION_PARENT_VALUE_ERROR("no corresponding values in %s.%s for value(s) %s in %s.%s") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      checkArgument(params != null);
      checkArgument(params.length == 2);
      checkArgument(params[0] instanceof String);
      checkArgument(params[1] instanceof List);
      return ImmutableMap.of(RELATION_SCHEMA, params[0], RELATION_COLUMNS, params[1]);
    }
  }, //
  /**
   * Duplicate values in unique field(s).
   */
  UNIQUE_VALUE_ERROR("invalid set of values (%s) for fields %s. Expected to be unique") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      return ImmutableMap.of();
    }
  }, //
  /**
   * Invalid value type (i.e. a string where an integer is expected).
   */
  VALUE_TYPE_ERROR("invalid value (%s) for field %s. Expected type is: %s") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      checkArgument(params != null);
      checkArgument(params.length == 1);
      checkArgument(params[0] instanceof ValueType);
      return ImmutableMap.of(EXPECTED_TYPE, params[0]);
    }
  }, //
  /**
   * Value out for (inclusive) range.
   */
  OUT_OF_RANGE_ERROR("number %d is out of range for field %s. Expected value between %d and %d") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      checkArgument(params != null);
      checkArgument(params.length == 2);
      checkArgument(params[0] instanceof Long);
      checkArgument(params[1] instanceof Long);

      return ImmutableMap.of(MIN_RANGE, params[0], MAX_RANGE, params[1]);
    }
  }, //
  /**
   * Range value is not numerical.
   */
  NOT_A_NUMBER_ERROR("%s is not a number for field %s. Expected a number") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      return ImmutableMap.of();
    }
  }, //
  /**
   * Missing required value.
   */
  MISSING_VALUE_ERROR("value missing for required field: %s") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      return ImmutableMap.of();
    }
  }, //
  /**
   * Values not in code list (as codes)
   */
  CODELIST_ERROR("invalid value %s for field %s. Expected code or value from CodeList %s") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      checkArgument(params != null);
      checkArgument(params.length == 1);
      return ImmutableMap.of(EXPECTED_VALUE, params[0]);
    }
  }, //
  /**
   * Values not in set of discrete values.
   */
  DISCRETE_VALUES_ERROR("invalid value %s for field %s. Expected one of the following values: %s") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      checkArgument(params != null);
      checkArgument(params.length == 1);
      checkArgument(params[0] instanceof Set);
      return ImmutableMap.of(EXPECTED_VALUE, params[0]);
    }
  }, //
  /**
   * More than one file matches the schema pattern.
   */
  TOO_MANY_FILES_ERROR("more than one file matches the schema pattern") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      checkArgument(params != null);
      checkArgument(params.length == 1);
      checkArgument(params[0] instanceof List);
      return ImmutableMap.of(UNEXPECTED_VALUES, params[0]);
    }
  }, //
  /**
   * No matching file for referencED schema.
   * <p>
   * Example: for specimen, if donor is missing
   */
  RELATION_FILE_ERROR("relation to schema %s has no matching file") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      checkArgument(params != null);
      checkArgument(params.length == 1);
      checkArgument(params[0] instanceof String);
      return ImmutableMap.of(RELATION_SCHEMA, params[0]);
    }
  }, //
  /**
   * No matching file for referencING schema (only applicable if relation is set to bidirectional).
   * <p>
   * Example: for specimen, if sample is missing (but not if surgery is missing, as the relation from surgery is not
   * bidirectional)
   */
  REVERSE_RELATION_FILE_ERROR("relation from schema %s has no matching file and this relation imposes that there be one") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      checkArgument(params != null);
      checkArgument(params.length == 1);
      checkArgument(params[0] instanceof String);
      return ImmutableMap.of(RELATION_SCHEMA, params[0]);
    }
  }, //
  /**
   * Repeated field names found in header.
   */
  DUPLICATE_HEADER_ERROR("duplicate header found: %s") {
    @Override
    public final ImmutableMap<String, Object> build(Object... params) {
      checkArgument(params != null);
      checkArgument(params.length == 1);
      checkArgument(params[0] instanceof List);
      return ImmutableMap.of(UNEXPECTED_VALUES, params[0]);
    }
  };

  // TODO: make enum out of this (DCC-506) - field name(s) and current value(s) are systematically recorded, so these
  // keys only reflect extra parameters needed to describe the error
  private static final String EXPECTED_VALUE = "expectedValue";

  private static final String EXPECTED_TYPE = "expectedType";

  private static final String MIN_RANGE = "minRange";

  private static final String MAX_RANGE = "maxRange";

  private static final String ACTUAL_NUM_COLUMNS = "actualNumColumns";

  private static final String RELATION_SCHEMA = "relationSchema";

  private static final String RELATION_COLUMNS = "relationColumnNames";

  private static final String UNEXPECTED_VALUES = "unexpectedValues";

  private final String message;

  private final boolean structural;

  public abstract ImmutableMap<String, Object> build(@Nullable Object... params);

  ValidationErrorCode(String message) {
    this(message, false);
  }

  ValidationErrorCode(String message, boolean structural) {
    this.message = message;
    this.structural = structural;
  }

  public String format(Map<String, ? extends Object> parameters) {
    // The formatted message doesn't make sense anymore since the column name was moved to TupleError
    // return String.format(message, terms);
    return this.message;
  }

  public boolean isStructural() {
    return structural;
  }

  public static String format(TupleError error) {
    checkArgument(error != null);
    return error.getCode().format(error.getParameters());
  }
}
