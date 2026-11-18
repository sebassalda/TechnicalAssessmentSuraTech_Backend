# Salesforce — Data model for premium payments

> Fictional configuration. Meridian runs **a single Salesforce org** that hosts both the **CRM** (customers, contact-center cases) and the **policy core** (policies, premiums, payments). It is the system of record for all of them. The org is owned by the Salesforce team; the Payments Integration squad writes payment data only.

## Objects

### `Policy__c` (read-only for the integration)

| Field | Type | Notes |
|---|---|---|
| `Policy_Number__c` | Text(20), **External ID, Unique** | Format `POL-<country>-<9 digits>`, e.g. `POL-CO-000187342`. Upper case. |
| `Country__c` | Picklist | `MX`, `CO`, `CL` |
| `Status__c` | Picklist | `Active`, `Suspended`, `Cancelled`. **Never written by the integration.** |
| `Overdue_Amount__c` | Currency | Amount pending to reinstate a suspended policy. |

### `Premium_Payment__c` (written by the integration)

| Field | Type | Notes |
|---|---|---|
| `Payment_Key__c` | Text(80), **External ID, Unique** | Defined and populated by the integration. Used for upserts. |
| `Policy__c` | Lookup(`Policy__c`) | Set through the policy's external ID (see example below). |
| `Amount__c` | Currency(16, 2) | In the payment's currency. |
| `CurrencyIsoCode` | Picklist | `MXN`, `COP`, `CLP` (multi-currency is enabled). |
| `Status__c` | Picklist | `Pending`, `Captured`, `Failed`, `Refunded`. |
| `Provider__c` | Picklist | `Quetzal`, `Andina`, `Austral`. |
| `Provider_Transaction_Id__c` | Text(80) | The provider's charge, transaction or payment identifier. |
| `Status_Changed_At__c` | DateTime | Time of the state change at the provider. |
| `Last_Event_Id__c` | Text(80) | Last provider event or notification identifier applied. |

## Automation owned by the Salesforce team

A record-triggered flow runs on every insert or update of `Premium_Payment__c`:

- When a payment becomes `Captured` and the captured amount covers `Overdue_Amount__c` of a `Suspended` policy, the policy is **reinstated** (`Active`) and the customer is notified.
- When a `Captured` payment becomes `Refunded`, the flow recalculates `Overdue_Amount__c` and may **suspend** the policy again.

Consequently, a wrong `Status__c` on a payment has a direct effect on a customer's coverage.

## Writing payments

Upsert by external ID (one record):

```http
PATCH /services/data/vNN.N/sobjects/Premium_Payment__c/Payment_Key__c/{paymentKey}
Content-Type: application/json

{
  "Policy__r": { "Policy_Number__c": "POL-CO-000187342" },
  "Amount__c": 185000.00,
  "CurrencyIsoCode": "COP",
  "Status__c": "Captured",
  "Provider__c": "Andina",
  "Provider_Transaction_Id__c": "TRX-CO-5521907",
  "Status_Changed_At__c": "2026-11-28T19:03:09Z",
  "Last_Event_Id__c": "ntf-8f1c2a77"
}
```

- `201 Created` when the record is inserted, `200 OK` or `204 No Content` when an existing record is updated.
- If the referenced policy number does not exist, the request fails with `400` and error code `INVALID_FIELD` (`Foreign key external ID: <value> not found for field Policy_Number__c in entity Policy__c`).

Upsert in bulk (up to **200 records per request**, counts as **one** API call):

```http
PATCH /services/data/vNN.N/composite/sobjects/Premium_Payment__c/Payment_Key__c
Content-Type: application/json

{ "allOrNone": false, "records": [ { "attributes": { "type": "Premium_Payment__c" }, "Payment_Key__c": "...", ... } ] }
```

With `allOrNone: false`, each record succeeds or fails independently and the response contains one result per record.
