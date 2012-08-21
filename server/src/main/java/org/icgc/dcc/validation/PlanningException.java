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

import org.icgc.dcc.validation.cascading.TupleState;

public class PlanningException extends RuntimeException {

  private String schemaName;

  private TupleState tupleState;

  public PlanningException() {
    super();
  }

  public PlanningException(String message, Throwable cause) {
    super(message, cause);
  }

  public PlanningException(String message) {
    super(message);
  }

  public PlanningException(Throwable cause) {
    super(cause);
  }

  public PlanningException(String schemaName, ValidationErrorCode errorCode, Object... parameters) {
    super();
    this.schemaName = schemaName;
    this.tupleState = new TupleState();
    tupleState.reportError(errorCode, parameters);
  }

  public String getSchemaName() {
    return this.schemaName;
  }

  public TupleState getTupleState() {
    return this.tupleState;
  }
}