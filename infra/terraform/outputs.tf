output "api_url" {
  description = "URL pública da API de pedidos"
  value       = "http://${aws_lb.main.dns_name}/orders"
}

output "ecr_repositories" {
  description = "Repositórios para o push das imagens"
  value       = { for name, repo in aws_ecr_repository.service : name => repo.repository_url }
}

output "kafka_bootstrap_brokers" {
  value = aws_msk_cluster.main.bootstrap_brokers
}
