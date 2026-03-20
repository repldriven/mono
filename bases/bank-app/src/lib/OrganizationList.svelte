<script>
  import { create_organization, list_organizations } from "./api.mjs";
  import { time_ago } from "./time.mjs";
  import { onMount } from "svelte";
  import Modal from "./Modal.svelte";

  let { selectedOrgId, onSelectDefault, onCreated, onLoaded, showToast } = $props();

  let organizations = $state([]);
  let loading = $state(false);
  let error = $state(null);

  let modalOpen = $state(false);
  let orgName = $state("");
  let currencies = $state("GBP");
  let creating = $state(false);

  function errorDetail(body) {
    if (!body) return null;
    return body.message ?? body.error ?? body.detail
           ?? (typeof body === "string" ? body : JSON.stringify(body));
  }

  async function load() {
    loading = true;
    error = null;
    try {
      const res = await list_organizations();
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        organizations = res.body.organizations ?? [];
        onLoaded?.(organizations);
      } else {
        error = res.body?.error ?? `HTTP ${res["http-status"]}`;
      }
    } catch (err) {
      error = err.message;
    } finally {
      loading = false;
    }
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (!orgName.trim()) return;
    creating = true;
    try {
      const currencyList = currencies.split(",").map(c => c.trim().toUpperCase()).filter(c => c);
      const res = await create_organization(orgName.trim(), currencyList);
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        orgName = "";
        currencies = "GBP";
        modalOpen = false;
        showToast?.({ type: "success", message: "Organization created" });
        await load();
        onCreated?.(organizations);
      } else {
        showToast?.({ type: "warning", message: errorDetail(res.body) ?? `HTTP ${res["http-status"]}` });
      }
    } catch (err) {
      showToast?.({ type: "error", message: err.message });
    } finally {
      creating = false;
    }
  }

  onMount(() => load());
</script>

<section>
  <div class="header">
    <h2>
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M3 1a1 1 0 0 0-1 1v12a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1V2a1 1 0 0 0-1-1H3zm1 2h2v2H4V3zm0 4h2v2H4V7zm0 4h2v2H4v-2zm4-8h2v2H8V3zm0 4h2v2H8V7zm0 4h2v2H8v-2z"/></svg>
      Organizations
    </h2>
    <div class="header-actions">
      <button class="new-btn" onclick={() => modalOpen = true}>+ New Organization</button>
      <button class="refresh" onclick={() => load()} disabled={loading}>
        {loading ? "Loading..." : "Refresh"}
      </button>
    </div>
  </div>

  <Modal open={modalOpen} onClose={() => modalOpen = false} title="New Organization">
    <form onsubmit={handleCreate}>
      <label>
        Organization Name
        <input
          type="text"
          bind:value={orgName}
          placeholder="Organization name"
          required
          disabled={creating}
        />
      </label>
      <label>
        Currencies
        <input
          type="text"
          bind:value={currencies}
          placeholder="GBP, USD"
          required
          disabled={creating}
        />
      </label>
      <button type="submit" disabled={creating || !orgName.trim()}>
        {creating ? "Creating..." : "Create Organization"}
      </button>
    </form>
  </Modal>

  {#if error}
    <div class="error-msg">{error}</div>
  {/if}

  <table>
    <thead>
      <tr>
        <th>ID</th>
        <th>Name</th>
        <th>Type</th>
        <th>Status</th>
        <th>Created</th>
        <th>Updated</th>
        <th>Action</th>
      </tr>
    </thead>
    <tbody>
      {#if organizations.length === 0 && !loading}
        <tr><td colspan="7" class="empty">No organizations</td></tr>
      {/if}
      {#each organizations as org}
        <tr>
          <td class="mono">{org["organization-id"]}</td>
          <td>{org.name}</td>
          <td>
            <span class="type-badge" class:internal={org.type === "internal"}>
              {org.type}
            </span>
          </td>
          <td>
            <span class="status-badge"
                  class:active={org.status === "active"}>
              {org.status}
            </span>
          </td>
          <td title={org["created-at"]}>{time_ago(org["created-at"])}</td>
          <td title={org["updated-at"]}>{time_ago(org["updated-at"])}</td>
          <td>
            {#if org["organization-id"] === selectedOrgId}
              <span class="default-badge">Default</span>
            {:else if org.type !== "internal"}
              <button
                class="action-btn"
                onclick={() => onSelectDefault(org["organization-id"])}
              >
                Set Default
              </button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
</section>

<style>
  section {
    margin-top: 0;
  }

  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 1rem;
  }

  .header-actions {
    display: flex;
    gap: 0.5rem;
  }

  h2 {
    margin: 0;
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .new-btn {
    padding: 0.4rem 0.8rem;
    background: #16a34a;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.85rem;
    cursor: pointer;
  }

  .new-btn:hover {
    background: #15803d;
  }

  .refresh {
    padding: 0.4rem 0.8rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.85rem;
    cursor: pointer;
  }

  .refresh:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  form {
    display: flex;
    flex-direction: column;
    gap: 1rem;
  }

  form label {
    display: flex;
    flex-direction: column;
    gap: 0.25rem;
    font-weight: 500;
  }

  form input {
    padding: 0.5rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.9rem;
    background: var(--bg-input);
    color: var(--text);
  }

  form button {
    padding: 0.5rem 1rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.9rem;
    cursor: pointer;
  }

  form button:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .error-msg {
    background: var(--bg-error);
    border: 1px solid var(--border-error);
    padding: 0.75rem;
    border-radius: 4px;
    margin-bottom: 1rem;
  }

  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.9rem;
  }

  th, td {
    text-align: left;
    padding: 0.5rem 0.6rem;
    border-bottom: 1px solid var(--border);
  }

  th {
    background: var(--bg-secondary);
    font-weight: 600;
    font-size: 0.8rem;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: var(--text-muted);
  }

  .mono {
    font-family: monospace;
    font-size: 0.8rem;
  }

  .empty {
    text-align: center;
    color: var(--text-faint);
    padding: 1.5rem;
  }

  .type-badge {
    display: inline-block;
    padding: 0.15rem 0.45rem;
    border-radius: 4px;
    font-size: 0.8rem;
    font-weight: 600;
    background: #e0e7ff;
    color: #3730a3;
  }

  .type-badge.internal {
    background: #fef3c7;
    color: #92400e;
  }

  .status-badge {
    display: inline-block;
    padding: 0.15rem 0.45rem;
    border-radius: 4px;
    font-size: 0.8rem;
    font-weight: 600;
  }

  .status-badge.active {
    background: #dcfce7;
    color: #166534;
  }

  .default-badge {
    display: inline-block;
    padding: 0.2rem 0.5rem;
    background: #dbeafe;
    color: #1d4ed8;
    border-radius: 4px;
    font-size: 0.8rem;
    font-weight: 600;
  }

  .action-btn {
    padding: 0.25rem 0.6rem;
    background: #2563eb;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.8rem;
    cursor: pointer;
  }

  .action-btn:hover {
    background: #1d4ed8;
  }
</style>
