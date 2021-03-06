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


mediator = require 'mediator'
View = require 'views/base/view'
Model = require 'models/base/model'
NextRelease = require 'models/next_release'
template = require 'views/templates/submission/cancel_submission'

module.exports = class CancelSubmissionView extends View
  template: template
  template = null

  container: '#page-container'
  containerMethod: 'append'
  autoRender: true
  tagName: 'div'
  className: "modal hide fade"
  id: 'cancel-submission-popup'

  initialize: ->
    #console.debug "ValidateSubmissionView#initialize", @options
    @model = new Model @options.submission.getAttributes()
    
    #release = new NextRelease()
    #release.fetch
    #  success: (data) =>
    #    @model.set 'queue', data.get('queue').length

    super

    @modelBind 'change', @render
    @delegate 'click', '#cancel-submission-button', @cancelSubmission

  cancelSubmission: (e) ->
    #console.debug "CancelSubmissionView", @model

    @$el.find('#cancel-submission-button').attr('disabled', 'disabled')
    @$el.find('#cancel-submission-button').html('Cancelling...')

    nextRelease = new NextRelease()

    nextRelease.cancel {key: @options.submission.get("projectKey")},
      success: =>
        @$el.modal 'hide'

        mediator.publish "cancelSubmission"
        mediator.publish "notify", "Validation for Project "+
          "#{@model.get('projectName')} has been cancelled."

