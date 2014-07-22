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
package org.icgc.dcc.core.util;

import static lombok.AccessLevel.PRIVATE;
import static org.icgc.dcc.core.util.Joiners.INDENT;
import static org.icgc.dcc.core.util.Splitters.NEWLINE;

import java.io.File;
import java.util.List;

import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Common object mappers.
 */
@NoArgsConstructor(access = PRIVATE)
public final class Jackson {

  public static final ObjectMapper DEFAULT = new ObjectMapper();
  public static final ObjectWriter PRETTY_WRITTER = DEFAULT.writerWithDefaultPrettyPrinter();

  public static String formatPrettyJson(String jsonString) {
    return formatPrettyJson(toJsonNode(jsonString));
  }

  @SneakyThrows
  public static String formatPrettyJson(Object object) {
    return PRETTY_WRITTER.writeValueAsString(object);
  }

  public static JsonNode getJsonRoot(String path) {
    return getJsonRoot(new File(path));
  }

  @SneakyThrows
  public static JsonNode getJsonRoot(File file) {
    return DEFAULT.readTree(file);
  }

  public static <T> JsonNode to(T t) {
    return DEFAULT.convertValue(t, JsonNode.class);
  }

  public static <T> T from(JsonNode jsonNode, Class<T> type) {
    return DEFAULT.convertValue(jsonNode, type);
  }

  public static <T> List<T> from(ArrayNode arrayNode, Class<T> type) {
    return DEFAULT.convertValue(
        arrayNode,
        new TypeReference<List<T>>() {});
  }

  public static <T> String formatPrettyLog(String message, T t) {
    return INDENT.join(
        message,
        INDENT.join(
            NEWLINE.split(
                formatPrettyJson(t))));
  }

  @SneakyThrows
  private static JsonNode toJsonNode(String jsonString) {
    return DEFAULT.readTree(jsonString);
  }

}
