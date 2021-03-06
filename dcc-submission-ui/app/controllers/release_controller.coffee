"""
* Copyright 2016(c) The Ontario Institute for Cancer Research.
* All rights reserved.
*
* This program and the accompanying materials are made available under the
* terms of the GNU Public License v3.0.
* You should have received a copy of the GNU General Public License along with
* this program. If not, see <http://www.gnu.org/licenses/>.
*
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
* AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
* IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
* ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
* LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
* CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
* SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
* INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
* CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
* ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
"""


Chaplin = require 'chaplin'

BaseController = require 'controllers/base/controller'

Release = require 'models/release'
Submission = require 'models/submission'
Releases = require 'models/releases'
SchemaReport = require 'models/schema_report'

ReleasesView = require 'views/release/releases_view'
ReleaseView = require 'views/release/release_view'
SubmissionView = require 'views/submission/submission_view'
SchemaReportPageView = require 'views/report/schema_report_page_view'

module.exports = class ReleaseController extends BaseController

  title: 'Releases'

  historyURL: (params) ->
    if params.release then "releases/#{params.release}" else "releases"

  list: (params) ->
    #console.debug 'ReleaseController#list', params
    @collection = new Releases()
    @view = new ReleasesView {@collection}
    @collection.fetch()

  show: (params) ->
    #console.debug 'ReleaseController#show', params
    @title = params.release
    @model = new Release {name: params.release}
    @view = new ReleaseView {@model}
    @model.fetch
      success: => @view.render()
      error: ->
        if Chaplin.mediator.user
          Chaplin.mediator.publish 'notify',
            "Release #{params.release} not found.",
            'error'
          Chaplin.mediator.publish '!startupController', 'release', 'list'

  submission: (params) ->
    #console.debug 'ReleaseController#submission', params
    @title = "#{params.submission} - #{params.release}"
    @model = new Submission {release: params.release, name: params.submission}
    @view = new SubmissionView {@model}
    @model.fetch
      error: ->
        if Chaplin.mediator.user
          Chaplin.mediator.publish 'notify',
            "Submission #{params.submission} not found.",
            "error"
          Chaplin.mediator.publish '!startupController',
            'release', 'show'
            release: params.release

  report: (params) ->
    #console.debug 'ReleaseController#report', params
    @title = "#{params.report} - #{params.submission} - #{params.release}"
    @model = new SchemaReport {
      release: params.release
      submission: params.submission
      name: params.report
    }
    @view = new SchemaReportPageView {@model}
    @model.fetch
      error: ->
        if Chaplin.mediator.user
          Chaplin.mediator.publish 'notify',
            "File #{params.report} not found.",
            'error'
          Chaplin.mediator.publish '!startupController',
            'release', 'submission'
            release: params.release
            submission: params.submission
