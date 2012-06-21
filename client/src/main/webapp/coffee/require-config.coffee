# Configure the AMD module loader
requirejs.config
  # The path where your JavaScripts are located
  baseUrl: '/js/app/'
  # Specify the paths of vendor libraries
  paths:
    jquery: '../vendor/jquery-1.7.2'
    underscore: '../vendor/underscore-1.3.3'
    backbone: '../vendor/backbone-0.9.2'
    chaplin: '../vendor/chaplin'
    bootstrap: '../vendor/bootstrap.min'
    text: '../vendor/require-text-2.0.0'
    handlebars: '../vendor/handlebars-1.0.0.beta.6'
    
  # Underscore and Backbone are not AMD-capable per default,
  # so we need to use the AMD wrapping of RequireJS
  shim: 
    backbone:
      deps: ['underscore', 'jquery']
      exports: 'Backbone'
    underscore:
      exports: '_'
    bootstrap:
      deps: ['jquery']
    
  # For easier development, disable browser caching
  # Of course, this should be removed in a production environment
  #, urlArgs: 'bust=' +  (new Date()).getTime()

# Bootstrap the application
require ['dcc_submission_application'], (DccSubmissionApplication) ->
  dcc = new DccSubmissionApplication()
  dcc.initialize()