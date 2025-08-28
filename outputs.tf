output "lambda_api_endpoint" {
  description = "El URL del endpoint de la API Gateway para invocar la función Lambda."
  value       = aws_apigatewayv2_stage.api_stage.invoke_url
}