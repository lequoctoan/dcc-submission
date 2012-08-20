/**
 * Copyright 2012(c) The Ontario Institute for Cancer Research. All rights reserved.
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
package org.icgc.dcc.validation.visitor;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.icgc.dcc.dictionary.model.FileSchema;
import org.icgc.dcc.dictionary.model.Relation;
import org.icgc.dcc.validation.ExternalFlowPlanningVisitor;
import org.icgc.dcc.validation.ExternalPlanElement;
import org.icgc.dcc.validation.ValidationErrorCode;
import org.icgc.dcc.validation.cascading.TupleState;
import org.icgc.dcc.validation.cascading.TuplesUtils;
import org.icgc.dcc.validation.cascading.ValidationFields;
import org.icgc.dcc.validation.visitor.RelationPlanningVisitor.RelationPlanElement.NoNullBufferBase.ConditionalNoNullBuffer;
import org.icgc.dcc.validation.visitor.RelationPlanningVisitor.RelationPlanElement.NoNullBufferBase.NoNullBuffer;

import cascading.flow.FlowProcess;
import cascading.operation.BaseOperation;
import cascading.operation.Buffer;
import cascading.operation.BufferCall;
import cascading.pipe.CoGroup;
import cascading.pipe.Every;
import cascading.pipe.Pipe;
import cascading.pipe.assembly.Discard;
import cascading.pipe.assembly.Rename;
import cascading.pipe.joiner.LeftJoin;
import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;

/**
 * Creates {@code PlanElement}s for {@code Relation}.
 */
public class RelationPlanningVisitor extends ExternalFlowPlanningVisitor {

  private static final String NAME = "fk";

  @Override
  public void visit(Relation relation) {
    collect(new RelationPlanElement(getCurrentSchema(), relation));
  }

  static class RelationPlanElement implements ExternalPlanElement {

    private final String lhs;

    private final String[] lhsFields;

    private final String rhs;

    private final String[] rhsFields;

    private final List<Integer> optionals;

    private RelationPlanElement(FileSchema fileSchema, Relation relation) {
      this.lhs = fileSchema.getName();
      this.lhsFields = relation.getFields().toArray(new String[] {});
      this.rhs = relation.getOther();
      this.rhsFields = relation.getOtherFields().toArray(new String[] {});
      this.optionals = relation.getOptionals();
    }

    @Override
    public String describe() {
      return String.format("%s[%s:%s->%s:%s]", NAME, lhs, Arrays.toString(lhsFields), rhs, Arrays.toString(rhsFields));
    }

    @Override
    public String[] lhsFields() {
      return lhsFields;
    }

    @Override
    public String rhs() {
      return rhs;
    }

    @Override
    public String[] rhsFields() {
      return rhsFields;
    }

    @Override
    public Pipe join(Pipe lhsPipe, Pipe rhsPipe) {
      String[] requiredLhsFields = extractRequiredFields(lhsFields);
      String[] optionalLhsFields = extractOptionalFields(lhsFields);

      String[] renamedRhsFields = rename();
      String[] requiredRhsRenamedFields = extractRequiredFields(renamedRhsFields);
      String[] optionalRhsRenamedFields = extractOptionalFields(renamedRhsFields);

      rhsPipe = new Discard(rhsPipe, new Fields(ValidationFields.OFFSET_FIELD_NAME));
      rhsPipe = new Rename(rhsPipe, new Fields(rhsFields), new Fields(renamedRhsFields));
      Pipe pipe =
          new CoGroup(lhsPipe, new Fields(requiredLhsFields), rhsPipe, new Fields(requiredRhsRenamedFields),
              new LeftJoin());
      NoNullBufferBase noNullBufferBase =
          optionals.isEmpty() ? //
          new NoNullBuffer(lhs, rhs, lhsFields, rhsFields, renamedRhsFields) : //
          new ConditionalNoNullBuffer(lhs, rhs, lhsFields, rhsFields, requiredLhsFields, requiredRhsRenamedFields,
              optionalLhsFields, optionalRhsRenamedFields);
      return new Every(pipe, Fields.ALL, noNullBufferBase, Fields.RESULTS);
    }

    private String[] extractOptionalFields(String[] fields) {
      return extractFields(true, fields);
    }

    private String[] extractRequiredFields(String[] fields) {
      return extractFields(false, fields);
    }

    private String[] extractFields(boolean keepOptional, String[] fields) {
      int size = keepOptional ? optionals.size() : fields.length - optionals.size();
      String[] requiredFields = new String[size];
      int j = 0;
      for(int i = 0; i < fields.length; i++) {
        if(optionals.contains(i) == keepOptional) {
          requiredFields[j++] = fields[i];
        }
      }
      return requiredFields;
    }

    private String[] rename() {
      String[] renamed = new String[rhsFields.length];
      for(int i = 0; i < renamed.length; i++) {
        renamed[i] = rhs + "$" + rhsFields[i];
      }
      return renamed;
    }

    @SuppressWarnings("rawtypes")
    static abstract class NoNullBufferBase extends BaseOperation implements Buffer {

      protected final String lhs; // extremely useful for debugging...

      protected final String rhs;

      protected final String[] lhsFields;

      protected final String[] rhsFields;

      NoNullBufferBase(String lhs, String rhs, String[] lhsFields, String[] rhsFields) {
        super(lhsFields.length + rhsFields.length, new Fields(ValidationFields.STATE_FIELD_NAME));
        this.lhs = lhs;
        this.rhs = rhs;
        this.lhsFields = lhsFields;
        this.rhsFields = rhsFields;
      }

      /*
       * The offset was passed from internal flow in order to access the offset
       */
      protected int getLhsOffset(TupleEntry entry) {
        return entry.getInteger(ValidationFields.OFFSET_FIELD_NAME);
      }

      protected void reportRelationError(TupleState tupleState, Tuple offendingLhsTuple) {
        tupleState.reportError(ValidationErrorCode.MISSING_RELATION_ERROR, TuplesUtils.getObjects(offendingLhsTuple),
            Arrays.asList(lhsFields), rhs, Arrays.asList(rhsFields));
      }

      static final class NoNullBuffer extends NoNullBufferBase {

        private final String[] renamedRhsFields;

        NoNullBuffer(String lhs, String rhs, String[] lhsFields, String[] rhsFields, String[] renamedRhsFields) {
          super(lhs, rhs, lhsFields, rhsFields);
          this.renamedRhsFields = renamedRhsFields;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void operate(FlowProcess flowProcess, BufferCall bufferCall) {
          Iterator<TupleEntry> iter = bufferCall.getArgumentsIterator();
          while(iter.hasNext()) {
            TupleEntry entry = iter.next();

            if(TuplesUtils.hasValues(entry, renamedRhsFields) == false) {
              TupleState state = new TupleState(getLhsOffset(entry));
              reportRelationError(state, entry.selectTuple(new Fields(lhsFields)));
              bufferCall.getOutputCollector().add(new Tuple(state));
            }
          }
        }
      }

      static final class ConditionalNoNullBuffer extends NoNullBufferBase {

        private final String[] requiredLhsFields;

        private final String[] requiredRhsFields;

        private final String[] optionalLhsFields;

        private final String[] optionalRhsFields;

        private final int optionalSize;

        private final Comparator[] tupleComparators;

        ConditionalNoNullBuffer(String lhs, String rhs, String[] lhsFields, String[] rhsFields,
            String[] requiredLhsFields, String[] requiredRhsFields, String[] optionalLhsFields,
            String[] optionalRhsFields) {
          super(lhs, rhs, lhsFields, rhsFields);
          this.requiredLhsFields = requiredLhsFields;
          this.requiredRhsFields = requiredRhsFields;
          this.optionalLhsFields = optionalLhsFields;
          this.optionalRhsFields = optionalRhsFields;
          this.optionalSize = optionalLhsFields.length;
          checkArgument(optionalSize > 0);

          Comparator comparator = new Comparator() { // allows one side to be null
                @Override
                public int compare(Object object1, Object object2) {
                  return object1 == null || object2 == null ? 0 : ((String) object1).compareTo(((String) object2));
                }
              };
          this.tupleComparators = new Comparator[optionalSize];
          for(int i = 0; i < optionalRhsFields.length; i++) {
            this.tupleComparators[i] = comparator; // we can reuse the same for all fields
          }
        }

        @Override
        @SuppressWarnings("unchecked")
        public void operate(FlowProcess flowProcess, BufferCall bufferCall) {
          Iterator<TupleEntry> iter = bufferCall.getArgumentsIterator();
          TupleEntry group = bufferCall.getGroup();

          // potential memory issue discussed in DCC-300
          List<Entry<Tuple, Integer>> lhsOptionalTuples = new ArrayList<Entry<Tuple, Integer>>();
          List<Tuple> rhsOptionalTuples = new ArrayList<Tuple>();
          Tuple requiredLhsTuple = group.selectTuple(new Fields(requiredLhsFields));

          while(iter.hasNext()) {
            TupleEntry entry = iter.next();
            if(TuplesUtils.hasValues(entry, requiredRhsFields)) {
              if(TuplesUtils.hasValues(entry, optionalLhsFields)) {
                Tuple lhsOptionalTuple = entry.selectTuple(new Fields(optionalLhsFields));
                lhsOptionalTuples.add(new SimpleEntry<Tuple, Integer>(lhsOptionalTuple, getLhsOffset(entry)));
              }
              if(TuplesUtils.hasValues(entry, optionalRhsFields)) {
                rhsOptionalTuples.add(entry.selectTuple(new Fields(optionalRhsFields)));
              }
            }
          }

          /*
           * To keep track of reported errors (because there can be several specimen_id from the rhs) and we cannot use
           * a set for lhsOptionalTuples
           */
          Set<Integer> reported = new HashSet<Integer>();
          for(Entry<Tuple, Integer> lhsTupleToOffset : lhsOptionalTuples) {
            Tuple lhsOptionalTuple = lhsTupleToOffset.getKey();
            if(contains(rhsOptionalTuples, lhsOptionalTuple) == false) {
              int lhsOffset = lhsTupleToOffset.getValue();
              TupleState tupleState = new TupleState(lhsOffset);
              if(reported.contains(lhsOffset) == false) {
                Tuple offendingLhsTuple = // so as to avoid storing it all in memory (memory/computing tradeoff)
                    rebuildLhsTuple(requiredLhsFields, optionalLhsFields, requiredLhsTuple, lhsOptionalTuple);
                reportRelationError(tupleState, offendingLhsTuple);

                bufferCall.getOutputCollector().add(new Tuple(tupleState));
                reported.add(lhsOffset);
              }
            }
          }
        }

        private boolean contains(List<Tuple> tuples, Tuple tuple) {
          for(Tuple tupleTmp : tuples) {
            if(tupleTmp.compareTo(this.tupleComparators, tuple) == 0) {
              return true;
            }
          }
          return false;
        }

        private Tuple rebuildLhsTuple(String[] requiredLhsFields, String[] optionalLhsFields, Tuple requiredLhsObjects,
            Tuple optionalLhsObjects) {
          List<String> list = new ArrayList<String>(Arrays.asList(requiredLhsFields));
          list.addAll(Arrays.asList(optionalLhsFields));
          String[] disorderedLhsFields = list.toArray(new String[] {});
          Tuple disorderedLhsObjects = requiredLhsObjects.append(optionalLhsObjects);
          return new TupleEntry(new Fields(disorderedLhsFields), disorderedLhsObjects)
              .selectTuple(new Fields(lhsFields));
        }
      }
    }
  }
}
