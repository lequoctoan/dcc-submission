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

import com.google.common.collect.ImmutableList;
import lombok.Data;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.facet.Facet;
import org.elasticsearch.search.facet.Facets;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.icgc.dcc.portal.search.SearchQuery;

@Data
public final class GetManyResponse {

  private final ImmutableList<ResponseHit> hits;
  private final ImmutableList<ResponseFacet> facets;
  private final ResponsePagination pagination;

  public GetManyResponse(final SearchResponse response, final SearchQuery searchQuery) {
    // System.out.println(response);
    this.hits = buildResponseHits(response.getHits().getHits());
    this.facets = buildResponseFacets(response.getFacets());
    this.pagination = new ResponsePagination(response.getHits(), searchQuery);
  }

  private ImmutableList<ResponseFacet> buildResponseFacets(Facets facets) {
    ImmutableList.Builder<ResponseFacet> lb = new ImmutableList.Builder<ResponseFacet>();
    for (Facet facet : facets.facets()) {
      // TODO if I don't cast it I cannot get access to the term data
      // works for now because we only return term facets
      lb.add(new ResponseFacet((TermsFacet) facet));
    }
    return lb.build();
  }

  private ImmutableList<ResponseHit> buildResponseHits(SearchHit[] hits) {
    ImmutableList.Builder<ResponseHit> lb = new ImmutableList.Builder<ResponseHit>();
    for (SearchHit hit : hits) {
      lb.add(new ResponseHit(hit));
    }
    return lb.build();
  }
}
