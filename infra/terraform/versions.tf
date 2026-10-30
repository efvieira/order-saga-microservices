terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  # Estado remoto: crie o bucket antes e descomente.
  # backend "s3" {
  #   bucket       = "meu-bucket-de-estado"
  #   key          = "order-saga/terraform.tfstate"
  #   region       = "sa-east-1"
  #   use_lockfile = true
  # }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = var.project
      ManagedBy = "terraform"
    }
  }
}
