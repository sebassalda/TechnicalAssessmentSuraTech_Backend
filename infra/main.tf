locals {
  prefix      = "mm-${var.environment}-${var.country}"
  rg_name     = coalesce(var.resource_group_name, "rg-${local.prefix}")
  common_tags = merge(var.tags, { environment = var.environment, country = var.country, application = "payment-intake" })
}
resource "azurerm_resource_group" "main" {
  name     = local.rg_name
  location = var.location
  tags     = local.common_tags
}
resource "azurerm_log_analytics_workspace" "main" {
  name                = "law-${local.prefix}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "PerGB2018"
  retention_in_days   = 30
  tags                = local.common_tags
}
resource "azurerm_application_insights" "main" {
  name                = "appi-${local.prefix}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  workspace_id        = azurerm_log_analytics_workspace.main.id
  application_type    = "web"
  tags                = local.common_tags
}
resource "azurerm_servicebus_namespace" "main" {
  name                = "sb-${local.prefix}"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  sku                 = "Standard"
  tags                = local.common_tags
}
resource "azurerm_servicebus_queue" "payment_events" {
  name                                 = "premium-payment-events"
  namespace_id                         = azurerm_servicebus_namespace.main.id
  max_delivery_count                   = 10
  dead_lettering_on_message_expiration = true
  default_message_ttl                  = "P14D"
  lock_duration                        = "PT1M"
}
resource "azurerm_user_assigned_identity" "intake" {
  name                = "id-${local.prefix}-intake"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  tags                = local.common_tags
}
resource "azurerm_role_assignment" "servicebus_sender" {
  scope                = azurerm_servicebus_queue.payment_events.id
  role_definition_name = "Azure Service Bus Data Sender"
  principal_id         = azurerm_user_assigned_identity.intake.principal_id
}
resource "azurerm_key_vault" "main" {
  name                       = "kv-${replace(local.prefix, "-", "")}-intake"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  tenant_id                  = data.azurerm_client_config.current.tenant_id
  sku_name                   = "standard"
  rbac_authorization_enabled = true
  purge_protection_enabled   = true
  soft_delete_retention_days = 7
  tags                       = local.common_tags
}
data "azurerm_client_config" "current" {}
resource "azurerm_role_assignment" "keyvault_secrets_reader" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_user_assigned_identity.intake.principal_id
}
resource "azurerm_container_app_environment" "main" {
  name                       = "cae-${local.prefix}"
  location                   = azurerm_resource_group.main.location
  resource_group_name        = azurerm_resource_group.main.name
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  tags                       = local.common_tags
}
resource "azurerm_container_app" "intake" {
  name                         = "ca-${local.prefix}-intake"
  resource_group_name          = azurerm_resource_group.main.name
  container_app_environment_id = azurerm_container_app_environment.main.id
  revision_mode                = "Single"
  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.intake.id]
  }
  template {
    min_replicas = 1
    max_replicas = 3
    container {
      name   = "intake"
      image  = var.container_image
      cpu    = 0.5
      memory = "1Gi"
      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "postgres"
      }
    }
  }
  ingress {
    external_enabled = false
    target_port      = 8080
    transport        = "http"
    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }
  tags = local.common_tags
}
