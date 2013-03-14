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

package org.icgc.dcc.portal.repositories;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.Client;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.facet.FacetBuilders;
import org.icgc.dcc.portal.core.AllowedFields;
import org.icgc.dcc.portal.core.Indexes;
import org.icgc.dcc.portal.core.Types;
import org.icgc.dcc.portal.request.RequestSearchQuery;
import org.icgc.dcc.portal.services.FilterService;

public class ProjectRepository extends BaseRepository {

  @Inject
  public ProjectRepository(Client client) {
    super(client, Indexes.PROJECTS, Types.PROJECTS, AllowedFields.PROJECT);
  }

  // different
  SearchRequestBuilder addFacets(SearchRequestBuilder s, RequestSearchQuery requestSearchQuery) {
    return s
        .addFacet(
            FacetBuilders.termsFacet("project_name").field("project_name")
                .facetFilter(setFacetFilter("project_name", requestSearchQuery.getFilters())).size(Integer.MAX_VALUE)
                .global(true))
        .addFacet(
            FacetBuilders.termsFacet("primary_site").field("primary_site")
                .facetFilter(setFacetFilter("primary_site", requestSearchQuery.getFilters())).size(Integer.MAX_VALUE)
                .global(true))
        .addFacet(
            FacetBuilders.termsFacet("country").field("country")
                .facetFilter(setFacetFilter("country", requestSearchQuery.getFilters())).size(Integer.MAX_VALUE)
                .global(true))
        .addFacet(
            FacetBuilders.termsFacet("available_profiling_data").field("available_profiling_data")
                .facetFilter(setFacetFilter("available_profiling_data", requestSearchQuery.getFilters()))
                .size(Integer.MAX_VALUE).global(true));
  }

  // different
  QueryBuilder buildQuery() {
    return QueryBuilders.matchAllQuery();
  }

  // different
  FilterBuilder buildFilters(JsonNode filters) {
    if (filters == null) {
      return FilterBuilders.matchAllFilter();
    } else {
      return FilterService.createProjectFilters(filters);
    }
  }
}
