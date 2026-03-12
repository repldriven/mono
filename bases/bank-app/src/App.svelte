<script>
  import { set_org } from "./lib/api.mjs";
  import Sidebar from "./lib/Sidebar.svelte";
  import OrgSelector from "./lib/OrgSelector.svelte";
  import OrganizationList from "./lib/OrganizationList.svelte";
  import CreateParty from "./lib/CreateParty.svelte";
  import PartyList from "./lib/PartyList.svelte";
  import AccountList from "./lib/AccountList.svelte";

  let currentPage = $state("organizations");
  let organizations = $state([]);
  let selectedOrgId = $state(null);
  let partyListRef = $state();
  let accountListRef = $state();

  let hasApiKey = $derived(selectedOrgId != null);

  function selectOrg(orgId) {
    selectedOrgId = orgId;
    set_org(orgId);
    partyListRef?.load();
    accountListRef?.load();
  }

  function handleOrgCreated(orgs) {
    organizations = orgs;
    if (!selectedOrgId && orgs.length > 0) {
      selectOrg(orgs[0]["organization-id"]);
    }
  }

  function handleOrgsLoaded(orgs) {
    organizations = orgs;
    if (!selectedOrgId && orgs.length > 0) {
      selectOrg(orgs[0]["organization-id"]);
    }
  }
</script>

<div class="layout">
  <Sidebar {currentPage} onNavigate={(page) => currentPage = page} />
  <main>
    {#if currentPage === "organizations"}
      <OrganizationList
        {selectedOrgId}
        onSelectDefault={(id) => selectOrg(id)}
        onCreated={handleOrgCreated}
        onLoaded={handleOrgsLoaded}
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
      <CreateParty onCreated={() => partyListRef?.load()} />
      <PartyList bind:this={partyListRef}
                 onAccountOpened={() => accountListRef?.load()} />
    {:else if currentPage === "accounts"}
      <OrgSelector
        {organizations}
        {selectedOrgId}
        onSelect={(id) => selectOrg(id)}
      />
      <AccountList bind:this={accountListRef} />
    {/if}
  </main>
</div>

<style>
  .layout {
    display: flex;
    height: 100vh;
    font-family: system-ui, -apple-system, sans-serif;
  }

  main {
    flex: 1;
    padding: 2rem;
    overflow-y: auto;
    max-width: 900px;
  }

  .no-org {
    padding: 2rem;
    text-align: center;
    color: #6b7280;
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
