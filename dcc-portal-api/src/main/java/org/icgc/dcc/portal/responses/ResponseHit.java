/*
 * Copyright 2013(c) The Ontario Institute for Cancer Research. All rights reserved.
 * 
 * This program and the accompanying materials are made available under the terms of the GNU Public
 * License v3.0. You should have received a copy of the GNU General Public License along with this
 * program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.icgc.dcc.portal.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHitField;

import java.util.Map;

import static org.icgc.dcc.portal.core.JsonUtils.MAPPER;

@Data
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ResponseHit {
  private final String id;
  private final String type;
  private final Float score;
  private final ObjectNode fields;

  public ResponseHit(SearchHit hit) {
    this.id = hit.getId();
    this.type = hit.getType();
    this.score = Float.isNaN(hit.getScore()) ? 0.0f : hit.getScore();
    this.fields = hit.getFields() == null ? null : buildSearchHitFields(hit.getFields());
  }

  private ObjectNode buildSearchHitFields(Map<String, SearchHitField> fields) {
    ObjectNode jNode = MAPPER.createObjectNode();
    for (SearchHitField field : fields.values()) {
      String name = field.getName();
      Object value = field.getValue();
      jNode.set(name, MAPPER.convertValue(value, JsonNode.class));
    }
    return jNode;
  }
}
