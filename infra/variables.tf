variable "subscription_id" {
  type        = string
  description = "Azure subscription ID, supplied at validation/deployment time"
}
variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "test", "prod"], var.environment)
    error_message = "environment must be dev, test or prod"
  }
}
variable "country" {
  type = string
  validation {
    condition     = contains(["mx", "co", "cl"], var.country)
    error_message = "country must be mx, co or cl"
  }
}
variable "location" {
  type    = string
  default = "eastus2"
}
variable "resource_group_name" {
  type    = string
  default = null
}
variable "container_image" {
  type        = string
  description = "Immutable container image reference (digest preferred)"
}
variable "container_registry_server" {
  type        = string
  description = "Registry hostname; configure image pull permissions separately"
}
variable "tags" {
  type    = map(string)
  default = {}
}
