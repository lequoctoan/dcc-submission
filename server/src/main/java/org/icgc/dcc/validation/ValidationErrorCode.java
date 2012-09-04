package org.icgc.dcc.validation;

import static com.google.common.base.Preconditions.checkArgument;

import org.icgc.dcc.validation.cascading.TupleState.TupleError;

public enum ValidationErrorCode {
  UNKNOWN_COLUMNS_WARNING("value for unknown column: %s"), //
  STRUCTURALLY_INVALID_ROW_ERROR("structurally invalid row: %s columns against %s declared in the header (row will be ignored by the rest of validation)", true), //
  RELATION_ERROR("invalid value(s) (%s) for field(s) %s.%s. Expected to match value(s) in: %s.%s"), //
  RELATION_PARENT_ERROR("no corresponding values in %s.%s for value(s) %s in %s.%s"), //
  UNIQUE_VALUE_ERROR("invalid set of values (%s) for fields %s. Expected to be unique"), //
  VALUE_TYPE_ERROR("invalid value (%s) for field %s. Expected type is: %s"), //
  OUT_OF_RANGE_ERROR("number %d is out of range for field %s. Expected value between %d and %d"), //
  NOT_A_NUMBER_ERROR("%s is not a number for field %s. Expected a number"), //
  MISSING_VALUE_ERROR("value missing for required field: %s"), //
  CODELIST_ERROR("invalid value %s for field %s. Expected code or value from CodeList %s"), //
  DISCRETE_VALUES_ERROR("invalid value %s for field %s. Expected one of the following values: %s"), //
  TOO_MANY_FILES_ERROR("more than one file matches the schema pattern"), //
  INVALID_RELATION_ERROR("a required schema for this relation was not found"), //
  MISSING_SCHEMA_ERROR("no valid schema found"), //
  DUPLICATE_HEADER_ERROR("duplicate header found: %s"), //
  MISSING_CODE_REQUIRED_ERROR("required field has a missing code: %s");

  private final String message;

  private final boolean structural;

  ValidationErrorCode(String message) {
    this(message, false);
  }

  ValidationErrorCode(String message, boolean structural) {
    this.message = message;
    this.structural = structural;
  }

  public String format(Object[] parameters) {
    return String.format(message, parameters);
  }

  public boolean isStructural() {
    return structural;
  }

  public static String format(TupleError error) {
    checkArgument(error != null);
    return error.getCode().format(error.getParameters());
  }
}
