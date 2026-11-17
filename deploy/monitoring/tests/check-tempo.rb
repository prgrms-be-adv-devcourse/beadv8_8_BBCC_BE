require 'yaml'

{
  'tempo' => false,
  'tempo/private-receiver' => true
}.each do |profile, private_receiver|
  rendered = IO.popen(['kubectl', 'kustomize', "deploy/monitoring/#{profile}"], &:read)
  abort "#{profile}: render failed" unless $?.success?
  deployment = YAML.load_stream(rendered).find do |item|
    item && item['kind'] == 'Deployment' && item.dig('metadata', 'name') == 'tempo'
  end
  volumes = deployment.fetch('spec').fetch('template').fetch('spec').fetch('volumes')
  tls_volume = volumes.find { |volume| volume['name'] == 'receiver-tls' }
  abort "#{profile}: unexpected TLS volume" unless !!tls_volume == private_receiver
  if private_receiver && tls_volume.dig('secret', 'secretName') != 'tracing-tempo-server-tls'
    abort "#{profile}: unexpected TLS secret"
  end
end
