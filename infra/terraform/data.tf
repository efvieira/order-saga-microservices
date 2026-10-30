# --- PostgreSQL: uma instância com um schema por serviço (custo menor na demo) ---

resource "aws_db_subnet_group" "main" {
  name       = var.project
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "main" {
  identifier                  = var.project
  engine                      = "postgres"
  engine_version              = "17"
  instance_class              = var.db_instance_class
  allocated_storage           = 20
  db_name                     = "saga"
  username                    = "saga"
  manage_master_user_password = true # senha gerada e guardada no Secrets Manager
  db_subnet_group_name        = aws_db_subnet_group.main.name
  vpc_security_group_ids      = [aws_security_group.data.id]
  storage_encrypted           = true
  skip_final_snapshot         = true
}

# --- Kafka gerenciado (MSK) ---

resource "aws_msk_cluster" "main" {
  cluster_name           = var.project
  kafka_version          = "3.8.x"
  number_of_broker_nodes = length(aws_subnet.private)

  broker_node_group_info {
    instance_type   = var.kafka_instance_type
    client_subnets  = aws_subnet.private[*].id
    security_groups = [aws_security_group.data.id]

    storage_info {
      ebs_storage_info {
        volume_size = 20
      }
    }
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS_PLAINTEXT"
      in_cluster    = true
    }
  }
}
