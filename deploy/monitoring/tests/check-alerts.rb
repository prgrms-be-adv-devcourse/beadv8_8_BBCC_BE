# frozen_string_literal: true

# Run: ruby deploy/monitoring/tests/check-alerts.rb /path/to/promtool
# Only the PromQL threshold/for behavior is tested here. Grafana/Slack require dev verification.
require 'yaml'
require 'tmpdir'
require 'fileutils'

source = File.expand_path('../grafana/provisioning/alerting/alerting.yml', __dir__)
rules = YAML.load_file(source).fetch('groups').flat_map { |group| group.fetch('rules') }.map do |rule|
  query = rule.fetch('data').find { |data| data.fetch('refId') == 'A' }.fetch('model').fetch('expr')
  threshold = rule.fetch('data').find { |data| data.fetch('refId') == 'B' }
                  .fetch('model').fetch('conditions').first.fetch('evaluator')
  operator = { 'lt' => '<', 'gt' => '>' }.fetch(threshold.fetch('type'))
  { 'alert' => rule.fetch('uid'), 'expr' => "(#{query}) #{operator} #{threshold.fetch('params').first}", 'for' => rule.fetch('for') }
end
Dir.mktmpdir('kidly-alert-check-') do |directory|
  File.write(File.join(directory, 'rules.yml'), { 'groups' => [{ 'name' => 'kidly', 'rules' => rules }] }.to_yaml)
  FileUtils.cp(File.join(__dir__, 'alerts.test.yml'), File.join(directory, 'tests.yml'))
  tool = File.expand_path(ARGV.fetch(0))
  Dir.chdir(directory) do
    abort 'Rule syntax check failed' unless system(tool, 'check', 'rules', 'rules.yml')
    abort 'Alert behavior check failed' unless system(tool, 'test', 'rules', 'tests.yml')
  end
end
