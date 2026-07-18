$env:REDIS_ENABLED = "true"
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"
$env:REDIS_PASSWORD = ""
$env:REDIS_SSL_ENABLED = "false"
$env:SPRING_DATA_REDIS_URL = ""
$env:SPRING_DATA_REDIS_HOST = "localhost"
$env:SPRING_DATA_REDIS_PORT = "6379"
$env:SPRING_DATA_REDIS_PASSWORD = ""
$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "edushift"
$env:DB_USER = "edushift"
$env:DB_PASSWORD = "edushift"
$env:DB_SCHEMA = "edushift"
$env:DB_SSL_MODE = "disable"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/edushift?currentSchema=edushift&sslmode=disable&ApplicationName=edushift-back&options=-c%20TimeZone%3DUTC%20-c%20search_path%3Dedushift,public"
$env:JWT_SECRET = "dev-render-sim-secret-please-change-32chars-min!!"
$env:JWT_ISSUER = "edushift"
$env:JWT_AUDIENCE = "edushift-clients"
$env:APP_BASE_URL = "http://localhost:8080"
$env:FRONTEND_URL = "http://localhost:5173"
$env:CORS_ALLOWED_ORIGINS = "http://localhost:5173"
$env:OPENROUTER_ENABLED = "false"
$env:OPENROUTER_API_KEY = "disabled"
$env:FIREBASE_ENABLED = "false"
$env:FIREBASE_PROJECT_ID = "disabled"
$env:HIKARI_MAXIMUM_POOL_SIZE = "5"
$env:HIKARI_MINIMUM_IDLE = "2"
$env:FLYWAY_LOG_LEVEL = "INFO"
$args = @(
  '-Dspring.devtools.restart.enabled=false',
  '-Dlogging.level.org.springframework.boot.context.config=DEBUG',
  '-Dlogging.level.org.springframework.core.env=DEBUG',
  '-jar', ".\target\edushift-back-0.0.1-SNAPSHOT.jar",
  '--spring.profiles.active=prod',
  '--server.port=8080',
  '--debug=true'
)
& java @args
