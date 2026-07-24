terraform {
  required_providers {
    digitalocean = {
      source  = "digitalocean/digitalocean"
      version = "~> 2.0"
    }
    local = {
      source  = "hashicorp/local"
      version = "~> 2.0"
    }
  }
}

# The DO API token is read from the DIGITALOCEAN_TOKEN environment variable.
# It is NEVER written in this file or committed to git.
provider "digitalocean" {}

# Ask DigitalOcean for the latest supported Kubernetes version,
# so we don't hard-code a version that might get retired.
data "digitalocean_kubernetes_versions" "current" {}

# A small, cheap cluster: enough to run our stack, quick to destroy.
resource "digitalocean_kubernetes_cluster" "lb" {
  name    = "loadbalancer"
  region  = "fra1" # Frankfurt - closest region to CZ
  version = data.digitalocean_kubernetes_versions.current.latest_version

  node_pool {
    name       = "worker-pool"
    size       = "s-2vcpu-2gb" # small node
    node_count = 2
  }
}

# Write the cluster's kubeconfig to a local file so kubectl can use it.
# This file holds cluster credentials, so it is gitignored.
resource "local_file" "kubeconfig" {
  content         = digitalocean_kubernetes_cluster.lb.kube_config[0].raw_config
  filename        = "${path.module}/kubeconfig.yaml"
  file_permission = "0600"
}

output "cluster_name" {
  value = digitalocean_kubernetes_cluster.lb.name
}
