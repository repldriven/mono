<script>
  import { set_org } from "./lib/api.mjs";
  import Sidebar from "./lib/Sidebar.svelte";
  import OrgSelector from "./lib/OrgSelector.svelte";
  import OrganizationList from "./lib/OrganizationList.svelte";
  import CreateParty from "./lib/CreateParty.svelte";
  import PartyList from "./lib/PartyList.svelte";
  import CashAccountList from "./lib/CashAccountList.svelte";
  import CashAccountProductList from "./lib/CashAccountProductList.svelte";
  import ApiKeyList from "./lib/ApiKeyList.svelte";
  import Toast from "./lib/Toast.svelte";

  let currentPage = $state("organizations");
  let organizations = $state([]);
  let selectedOrgId = $state(null);
  let partyListRef = $state();
  let accountListRef = $state();
  let productListRef = $state();
  let apiKeyListRef = $state();
  let toastRef = $state();

  let hasApiKey = $derived(selectedOrgId != null);

  function showToast(opts) {
    toastRef?.show(opts);
  }

  function selectOrg(orgId) {
    selectedOrgId = orgId;
    set_org(orgId);
    partyListRef?.load();
    accountListRef?.load();
    productListRef?.load();
    apiKeyListRef?.load();
  }

  function firstCustomerOrg(orgs) {
    return orgs.find(o => o.type !== "internal");
  }

  function handleOrgCreated(orgs) {
    organizations = orgs;
    if (!selectedOrgId) {
      const org = firstCustomerOrg(orgs);
      if (org) selectOrg(org["organization-id"]);
    }
  }

  function handleOrgsLoaded(orgs) {
    organizations = orgs;
    if (!selectedOrgId) {
      const org = firstCustomerOrg(orgs);
      if (org) selectOrg(org["organization-id"]);
    }
  }
</script>

<Toast bind:this={toastRef} />

<div class="layout">
  <Sidebar {currentPage} onNavigate={(page) => currentPage = page} />
  <main>
    {#if currentPage === "organizations"}
      <OrganizationList
        {selectedOrgId}
        onSelectDefault={(id) => selectOrg(id)}
        onCreated={handleOrgCreated}
        onLoaded={handleOrgsLoaded}
        {showToast}
      />
    {:else if !hasApiKey}
      <div class="no-org">
        <p>Create an organization first.</p>
        <button onclick={() => currentPage = "organizations"}>
          Go to Organizations
        </button>
      </div>
    {:else if currentPage === "parties"}
      <OrgSelector
        {organizations}
        {selectedOrgId}
        onSelect={(id) => selectOrg(id)}
      />
      <PartyList bind:this={partyListRef}
                 onAccountOpened={() => accountListRef?.load()}
                 {showToast}>
        {#snippet headerActions()}
          <CreateParty onCreated={() => partyListRef?.load()} {showToast} />
        {/snippet}
      </PartyList>
    {:else if currentPage === "accounts"}
      <OrgSelector
        {organizations}
        {selectedOrgId}
        onSelect={(id) => selectOrg(id)}
      />
      <CashAccountList bind:this={accountListRef} orgId={selectedOrgId} />
    {:else if currentPage === "products"}
      <OrgSelector
        {organizations}
        {selectedOrgId}
        onSelect={(id) => selectOrg(id)}
      />
      <CashAccountProductList bind:this={productListRef} {showToast} />
    {:else if currentPage === "api-keys"}
      <OrgSelector
        {organizations}
        {selectedOrgId}
        onSelect={(id) => selectOrg(id)}
      />
      <ApiKeyList bind:this={apiKeyListRef} />
    {/if}
  </main>
</div>

<style>
  :global(:root) {
    --bg: #ffffff;
    --bg-secondary: #f9fafb;
    --bg-hover: #e5e7eb;
    --bg-input: #ffffff;
    --bg-dropdown: #ffffff;
    --bg-error: #fee2e2;
    --bg-pagination: #f3f4f6;
    --text: #111827;
    --text-secondary: #374151;
    --text-muted: #6b7280;
    --text-faint: #9ca3af;
    --border: #e5e7eb;
    --border-input: #d1d5db;
    --border-error: #fca5a5;
    --details-border: #e5e7eb;
    color-scheme: light;
  }

  :global(:root.dark) {
    --bg: #0f172a;
    --bg-secondary: #1e293b;
    --bg-hover: #334155;
    --bg-input: #1e293b;
    --bg-dropdown: #1e293b;
    --bg-error: #450a0a;
    --bg-pagination: #1e293b;
    --text: #e2e8f0;
    --text-secondary: #cbd5e1;
    --text-muted: #94a3b8;
    --text-faint: #64748b;
    --border: #334155;
    --border-input: #475569;
    --border-error: #7f1d1d;
    --details-border: #334155;
    color-scheme: dark;
  }

  :global(body) {
    background: var(--bg);
    color: var(--text);
  }

  .layout {
    display: flex;
    height: 100vh;
    font-family: system-ui, -apple-system, sans-serif;
  }

  main {
    flex: 1;
    padding: 2rem;
    overflow-y: auto;
    max-width: 1400px;
    background: var(--bg);
    color: var(--text);
  }

  .no-org {
    padding: 2rem;
    text-align: center;
    color: var(--text-muted);
  }

  .no-org button {
    margin-top: 1rem;
    padding: 0.5rem 1rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    cursor: pointer;
  }
</style>
