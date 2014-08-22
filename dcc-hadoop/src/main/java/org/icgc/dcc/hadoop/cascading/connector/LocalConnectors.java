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
package org.icgc.dcc.hadoop.cascading.connector;

import static org.icgc.dcc.core.util.URIs.LOCAL_ROOT_URI;

import java.util.Map;

import lombok.NonNull;

import org.icgc.dcc.core.util.Hosts;

import cascading.flow.FlowConnector;
import cascading.flow.local.LocalFlowConnector;

class LocalConnectors extends BaseConnectors {

  @Override
  public FlowConnector getFlowConnector() {
    return new LocalFlowConnector();
  }

  @Override
  public FlowConnector getTestFlowConnector() {
    return getFlowConnector(Utils.getProperties(LOCAL_ROOT_URI, Hosts.LOCALHOST));
  }

  @Override
  public FlowConnector getFlowConnector(@NonNull final Map<?, ?> flowProperties) {
    return new LocalFlowConnector(toObjectsMap(flowProperties));
  }

}
