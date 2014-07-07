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

import static com.google.common.base.Joiner.on;
import static lombok.AccessLevel.PRIVATE;
import lombok.NoArgsConstructor;

import com.google.common.base.Joiner;

/**
 * Common joiners.
 */
@NoArgsConstructor(access = PRIVATE)
public final class Joiners {

  public static final Joiner WHITESPACE = on(Separators.WHITESPACE);
  public static final Joiner EMPTY_STRING = on(Separators.EMPTY_STRING);
  public static final Joiner SLASH = on('/');
  public static final Joiner TAB = on(Separators.TAB);
  public static final Joiner NEWLINE = on(Separators.NEWLINE);
  public static final Joiner DOT = on(".");
  public static final Joiner DASH = on("-");
  public static final Joiner UNDERSCORE = on("_");
  public static final Joiner COMMA = on(Separators.COMMA);
  public static final Joiner COLON = on(':');
  public static final Joiner SEMICOLON = on(';');
  public static final Joiner PATH = SLASH;
  public static final Joiner EXTENSION = DOT;
  public static final Joiner INDENT = on(Separators.INDENT);
  public static final Joiner CREDENTIALS = COLON;

}