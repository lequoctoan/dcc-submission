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

import java.io.File;

import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

/**
 * Common object mappers.
 */
@NoArgsConstructor(access = PRIVATE)
public final class Jackson {

  public static final ObjectMapper DEFAULT = new ObjectMapper();
  public static final ObjectWriter PRETTY_WRITTER = DEFAULT.writerWithDefaultPrettyPrinter();

  @SneakyThrows
  public static String toJsonPrettyString(String jsonString) {
    return PRETTY_WRITTER.writeValueAsString(DEFAULT.readTree(jsonString));
  }

  @SneakyThrows
  public static String toJsonPrettyString(Object object) {
    return PRETTY_WRITTER.writeValueAsString(object);
  }

  public static JsonNode getJsonRoot(String path) {
    return getJsonRoot(new File(path));
  }

  @SneakyThrows
  public static JsonNode getJsonRoot(File file) {
    return DEFAULT.readTree(file);
  }

  public static <T> JsonNode toJsonNode(T t) {
    return DEFAULT.convertValue(t, JsonNode.class);
  }

}