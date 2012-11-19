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
package org.icgc.dcc.validation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.icgc.dcc.dictionary.model.FileSchema;
import org.icgc.dcc.validation.cascading.HadoopJsonScheme;
import org.icgc.dcc.validation.cascading.TupleStateSerialization;
import org.icgc.dcc.validation.cascading.ValidationFields;

import cascading.flow.FlowConnector;
import cascading.flow.hadoop.HadoopFlowConnector;
import cascading.property.AppProps;
import cascading.scheme.hadoop.TextDelimited;
import cascading.scheme.hadoop.TextLine;
import cascading.tap.Tap;
import cascading.tap.hadoop.Hfs;
import cascading.tuple.Fields;
import cascading.tuple.hadoop.TupleSerializationProps;

import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

public class HadoopCascadingStrategy extends BaseCascadingStrategy {

  private final Config hadoopConfig;

  public HadoopCascadingStrategy(Config hadoopConfig, FileSystem fileSystem, Path source, Path output, Path system) {
    super(fileSystem, source, output, system);
    this.hadoopConfig = hadoopConfig;
  }

  @Override
  public FlowConnector getFlowConnector() {
    Map<Object, Object> properties = Maps.newHashMap();
    TupleSerializationProps.addSerialization(properties, TupleStateSerialization.class.getName());
    for(Map.Entry<String, ConfigValue> configEntry : hadoopConfig.entrySet()) {
      properties.put(configEntry.getKey(), configEntry.getValue().unwrapped());
    }
    properties.put("mapred.compress.map.output", true);
    properties.put("mapred.map.output.compression.codec", "org.apache.hadoop.io.compress.SnappyCodec");
    AppProps.setApplicationJarClass(properties, org.icgc.dcc.Main.class);
    return new HadoopFlowConnector(properties);
  }

  @Override
  public Tap<?, ?, ?> getReportTap(FileSchema schema, FlowType type, String reportName) {
    Path path = reportPath(schema, type, reportName);
    return new Hfs(new HadoopJsonScheme(), path.toUri().getPath());
  }

  @Override
  public InputStream readReportTap(FileSchema schema, FlowType type, String reportName) throws IOException {
    Path reportPath = reportPath(schema, type, reportName);
    if(fileSystem.isFile(reportPath)) {
      return fileSystem.open(reportPath);
    }

    List<InputSupplier<InputStream>> inputSuppliers = new ArrayList<InputSupplier<InputStream>>();
    for(FileStatus fileStatus : fileSystem.listStatus(reportPath)) {
      final Path filePath = fileStatus.getPath();
      if(fileStatus.isFile() && filePath.getName().startsWith("part-")) {
        InputSupplier<InputStream> inputSupplier = new InputSupplier<InputStream>() {
          @Override
          public InputStream getInput() throws IOException {
            return fileSystem.open(filePath);
          }
        };
        inputSuppliers.add(inputSupplier);
      }
    }

    return ByteStreams.join(inputSuppliers).getInput();
  }

  @Override
  protected Tap<?, ?, ?> tap(Path path) {
    return new Hfs(new TextDelimited(true, "\t"), path.toUri().getPath());
  }

  @Override
  protected Tap<?, ?, ?> tap(Path path, Fields fields) {
    return new Hfs(new TextDelimited(fields, true, "\t"), path.toUri().getPath());
  }

  @Override
  protected Tap<?, ?, ?> tapSource(Path path) {
    return new Hfs(new TextLine(new Fields(ValidationFields.OFFSET_FIELD_NAME, "line")), path.toUri().getPath());
  }
}
