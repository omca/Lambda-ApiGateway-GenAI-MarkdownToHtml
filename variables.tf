variable "aws_region" {
  description = "La región de AWS para desplegar los recursos."
  type        = string
  default     = "us-east-1"
}

variable "assistant_api_url" {
  description = "La URL de la API del asistente AI (ej. https://api.openai.com/v1/chat/completions)."
  type        = string
}

variable "assistant_api_key" {
  description = "La clave de la API del asistente AI."
  type        = string
  sensitive   = true
}