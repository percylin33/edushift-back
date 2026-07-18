$env:REDIS_ENABLED = "true"
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"
$env:REDIS_PASSWORD = ""
$env:REDIS_SSL_ENABLED = "false"
$env:SPRING_DATA_REDIS_URL = ""
Write-Host "REDIS_HOST=$env:REDIS_HOST"
Write-Host "REDIS_PORT=$env:REDIS_PORT"
$env:SPRING_DATA_REDIS_HOST = "localhost"
$env:SPRING_DATA_REDIS_PORT = "6379"
$args = @('-Dspring.devtools.restart.enabled=false', '-jar', ".\target\edushift-back-0.0.1-SNAPSHOT.jar", '--spring.profiles.active=prod', '--server.port=8081')
& java @args
