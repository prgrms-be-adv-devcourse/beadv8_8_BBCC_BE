# frozen_string_literal: true
require 'yaml'
paths = %w[deploy monitoring-deploy environment-operation monitoring-check ai-notes-groq-trial].map { |name| ".github/workflows/#{name}.yml" }
paths.each do |path|
  data = YAML.load_file(path)
  data.fetch('jobs').each do |name, job|
    declared = Array(job['needs'])
    referenced = job.to_yaml.scan(/\bneeds\.([A-Za-z0-9_-]+)\./).flatten.uniq
    missing = referenced - declared
    abort "#{path} #{name}: undeclared needs #{missing.join(', ')}" unless missing.empty?
  end
end
trial = YAML.load_file('.github/workflows/ai-notes-groq-trial.yml')
# Psych parses YAML 1.1 `on` as true.
trigger = trial['on'] || trial[true]
abort 'Groq trial must be manual only' unless trigger.keys == ['workflow_dispatch']
job = trial.fetch('jobs').fetch('dev-once')
abort 'Groq trial requires dev and explicit Free confirmation' unless job['environment'] == 'dev' && job['if'] == 'inputs.confirm_free_plan == true'
abort 'Groq trial must not enable xtrace' if job.to_yaml.match?(/set\s+-[^\n]*x/)
%w[deploy environment-operation].each do |name|
  concurrency = YAML.load_file(".github/workflows/#{name}.yml").fetch('concurrency')
  abort "#{name}: pending lifecycle requests can be replaced" unless concurrency['queue'] == 'max' && concurrency['cancel-in-progress'] == false
end
puts 'Workflow dependency and queue checks passed'
