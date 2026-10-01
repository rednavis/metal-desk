variable "name_prefix" {
  description = "Prefix of every resource name, metaldesk-<env>. Comes from the environment root's local.name_prefix."
  type        = string
}

variable "name" {
  description = "The service's short name, such as api. The Cloud Run service is named <name_prefix>-<name>."
  type        = string

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*[a-z0-9]$", var.name))
    error_message = "name must be lowercase letters, digits and hyphens, starting with a letter and not ending with a hyphen."
  }
}

variable "region" {
  description = "The region the service runs in. Must be the region of the network the egress settings come from."
  type        = string
}

variable "image" {
  description = <<-EOT
    The container image, pinned: either a digest (registry/path@sha256:<64 hex>) or a tag that is a commit SHA
    (registry/path:<7 to 40 hex>). There is no default, and a mutable tag, an untagged name and a version-looking tag
    are all rejected, so a rollback always points at one exact build.
  EOT
  type        = string

  validation {
    condition = can(regex(
      "^[a-z0-9.-]+(/[a-z0-9._-]+)+(@sha256:[a-f0-9]{64}|:[a-f0-9]{7,40})$", var.image
    ))
    error_message = "image must be pinned by digest (...@sha256:<64 hex>) or by commit-SHA tag (...:<7 to 40 hex>); a mutable tag such as :latest, a version tag and an untagged name are all rejected."
  }
}

variable "container_port" {
  description = "The port the application listens on. Cloud Run sends traffic here and sets PORT to the same value. The applications pin server.port in application.yml, so this must equal it (api 8082, pricing-bridge 8083, admin 8081)."
  type        = number

  validation {
    condition     = var.container_port >= 1 && var.container_port <= 65535
    error_message = "container_port must be a valid TCP port."
  }
}

variable "exposure" {
  description = <<-EOT
    Who can reach the service. The default is the closed one, so a service added without thought is never open.
      private                 - ingress internal, no invoker granted to anyone. Only callers named in invoker_members.
      internal-load-balancer  - ingress from internal sources and Google Cloud load balancers; still no public invoker.
                                What fronts admin (the load balancer, and IAP in T-076).
      public                  - ingress from anywhere and allUsers may invoke. An explicit opt-in; only api uses it.
  EOT
  type        = string
  default     = "private"

  validation {
    condition     = contains(["private", "internal-load-balancer", "public"], var.exposure)
    error_message = "exposure must be private, internal-load-balancer or public."
  }
}

variable "invoker_members" {
  description = "IAM members (for example serviceAccount:<email>) allowed to invoke the service, on this service only. Use it for service-to-service calls."
  type        = list(string)
  default     = []
}

variable "deployer_members" {
  description = "IAM members (serviceAccount:<email>) that may deploy new revisions of this service: the CI deployer (T-077). Granted roles/run.developer on THIS service and act-as on this service's own runtime account, and nothing else. Not roles/run.admin, which can also rewrite the service's IAM policy and so make it public."
  type        = list(string)
  default     = []
}

variable "min_instance_count" {
  description = "Instances kept running with no traffic. Above zero this bills continuously; record why where the service is declared."
  type        = number
  default     = 0

  validation {
    condition     = var.min_instance_count >= 0
    error_message = "min_instance_count cannot be negative."
  }
}

variable "max_instance_count" {
  description = "The most instances Cloud Run will start."
  type        = number
  default     = 10

  validation {
    condition     = var.max_instance_count >= 1 && var.max_instance_count >= var.min_instance_count
    error_message = "max_instance_count must be at least 1 and at least min_instance_count."
  }
}

variable "cpu_always_allocated" {
  description = "Keep CPU allocated outside requests. Needed by a service that works with no request in flight, such as pricing-bridge's market-data subscription; otherwise Cloud Run throttles it between requests."
  type        = bool
  default     = false
}

variable "cpu" {
  description = "CPU limit of the container."
  type        = string
  default     = "1"
}

variable "memory" {
  description = "Memory limit of the container. A Spring Boot JVM needs room beyond its heap; do not go below 512Mi."
  type        = string
  default     = "1Gi"
}

variable "env" {
  description = "Plain environment variables, name to value. Names that look like credentials are rejected: pass those through secret_env."
  type        = map(string)
  default     = {}

  validation {
    condition     = alltrue([for name in keys(var.env) : !can(regex("(?i)(secret|passw|token|key|credential|uri|dsn)", name))])
    error_message = "A plain environment variable name looks like it holds a credential or a connection string (it contains secret, passw, token, key, credential, uri or dsn). Pass it through secret_env as a Secret Manager reference."
  }

  validation {
    condition     = !contains(keys(var.env), "PORT")
    error_message = "PORT is set by Cloud Run from container_port; do not set it."
  }
}

variable "secret_env" {
  description = <<-EOT
    Environment variables whose value comes from Secret Manager, keyed by variable name. This is the only way to give
    the service anything sensitive, and it takes references, never values: secret is the secret's id and version a
    number. A version is always explicit so a deploy is reproducible. The secrets are created by T-075, which also
    grants this service's account access to each one.
  EOT
  type = map(object({
    secret  = string
    version = string
  }))
  default = {}

  validation {
    condition     = alltrue([for ref in values(var.secret_env) : can(regex("^[0-9]+$", ref.version))])
    error_message = "Each secret_env version must be a version number such as 3, never a moving alias."
  }
}

variable "vpc_egress" {
  description = "Direct VPC egress settings: the network module's cloud_run_egress output, passed whole. The network tag it carries is what the egress firewall rules select, so it must be set; egress is restricted to private ranges."
  type = object({
    network      = string
    subnetwork   = string
    network_tags = list(string)
    vpc_egress   = string
  })

  validation {
    condition     = var.vpc_egress.vpc_egress == "PRIVATE_RANGES_ONLY"
    error_message = "vpc_egress.vpc_egress must be PRIVATE_RANGES_ONLY: general internet egress is not allowed (ADR-0002)."
  }

  validation {
    condition     = length(var.vpc_egress.network_tags) > 0
    error_message = "vpc_egress.network_tags must not be empty: without the tag the egress firewall rules do not apply to the service."
  }
}

variable "health_path" {
  description = "The path of the startup and liveness probes. Architecture section 7 puts Actuator on /actuator."
  type        = string
  default     = "/actuator/health"
}

variable "startup_timeout_seconds" {
  description = "How long a starting instance may take to pass its startup probe before Cloud Run gives up on it. A JVM needs far longer than a native binary; shorter values show up as a crash loop. At most 240."
  type        = number
  default     = 180

  validation {
    condition     = var.startup_timeout_seconds >= 30 && var.startup_timeout_seconds <= 240
    error_message = "startup_timeout_seconds must be between 30 and 240 (Cloud Run's limit)."
  }
}

variable "deletion_protection" {
  description = "Refuse to destroy the service while true."
  type        = bool
  default     = true
}
