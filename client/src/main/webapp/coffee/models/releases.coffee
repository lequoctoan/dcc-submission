define (require) ->
	Collection = require 'models/base/collection'	

	"use strict"

	class Release extends Collection
		url: ""
		urlPath: ->
			"releases/"