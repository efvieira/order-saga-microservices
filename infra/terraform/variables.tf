variable "project" {
  description = "Prefixo dos recursos"
  type        = string
  default     = "order-saga"
}

variable "region" {
  description = "Região AWS"
  type        = string
  default     = "sa-east-1"
}

variable "image_tag" {
  description = "Tag das imagens no ECR (ex.: o SHA do commit)"
  type        = string
  default     = "latest"
}

variable "services" {
  description = "Serviços da saga: porta e se ficam expostos no load balancer"
  type = map(object({
    port   = number
    public = bool
  }))
  default = {
    "order-service"     = { port = 8081, public = true }
    "inventory-service" = { port = 8082, public = false }
    "payment-service"   = { port = 8083, public = false }
    "shipping-service"  = { port = 8084, public = false }
  }
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "kafka_instance_type" {
  type    = string
  default = "kafka.t3.small"
}
