# Define la región de AWS donde se desplegará
provider "aws" {
  region = var.aws_region
}

# --- 1. Paquete del código de la función Lambda ---
# Aquí se asume que ya has ejecutado 'mvn clean package' y tienes el JAR
data "archive_file" "lambda_zip" {
  type        = "zip"
  source_file = "target/lambda-orquestador-1.0.jar"
  output_path = "lambda_function.zip"
}

# --- 2. Rol de IAM para la función Lambda ---
resource "aws_iam_role" "lambda_role" {
  name = "lambda-orquestador-role-java"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

# Política de IAM para que Lambda pueda escribir logs
resource "aws_iam_role_policy_attachment" "lambda_policy" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# --- 3. La Función Lambda ---
resource "aws_lambda_function" "orquestador_lambda" {
  function_name = "orquestador-nutricion-api-java"
  filename      = data.archive_file.lambda_zip.output_path
  role          = aws_iam_role.lambda_role.arn
  handler       = "com.nutricion.OrquestadorLambda::handleRequest"
  runtime       = "java17"
  memory_size   = 512
  timeout       = 30


  # --- AÑADE ESTA LÍNEA ---
  source_code_hash = data.archive_file.lambda_zip.output_base64sha256
  # ------------------------

  # Variables de entorno para las credenciales de la API
  environment {
    variables = {
      ASSISTANT_API_URL = var.assistant_api_url
      ASSISTANT_API_KEY = var.assistant_api_key
    }
  }
}

# --- 4. API Gateway para exponer la función Lambda ---
resource "aws_apigatewayv2_api" "lambda_api" {
  name          = "orquestador-nutricion-api-gw-java"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_stage" "api_stage" {
  api_id      = aws_apigatewayv2_api.lambda_api.id
  name        = "$default"
  auto_deploy = true
}

resource "aws_apigatewayv2_integration" "lambda_integration" {
  api_id             = aws_apigatewayv2_api.lambda_api.id
  integration_uri    = aws_lambda_function.orquestador_lambda.invoke_arn
  integration_type   = "AWS_PROXY"
  integration_method = "POST"
}

resource "aws_apigatewayv2_route" "api_route" {
  api_id      = aws_apigatewayv2_api.lambda_api.id
  route_key   = "POST /"
  target      = "integrations/${aws_apigatewayv2_integration.lambda_integration.id}"
}

# --- 5. Permiso para que API Gateway invoque la Lambda ---
resource "aws_lambda_permission" "api_gateway_permission" {
  statement_id  = "AllowAPIGatewayInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.orquestador_lambda.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.lambda_api.execution_arn}/*/*"
}