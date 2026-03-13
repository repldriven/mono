<script>
  import { create_organization, list_organizations } from "./api.mjs";
  import { timeAgo } from "./time.mjs";
  import { onMount } from "svelte";

  let { selectedOrgId, onSelectDefault, onCreated, onLoaded } = $props();

  let organizations = $state([]);
  let loading = $state(false);
  let error = $state(null);

  let orgName = $state("");
  let creating = $state(false);
  let createError = $state(null);

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
    createError = null;
    try {
      const res = await create_organization(orgName.trim());
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        orgName = "";
        await load();
        onCreated?.(organizations);
      } else {
        createError = res.body?.error ?? `HTTP ${res["http-status"]}`;
      }
    } catch (err) {
      createError = err.message;
    } finally {
      creating = false;
    }
  }

  onMount(() => load());
</script>

<section>
  <div class="header">
    <h2>Organizations</h2>
    <button class="refresh" onclick={() => load()} disabled={loading}>
      {loading ? "Loading..." : "Refresh"}
    </button>
  </div>

  <form class="create-form" onsubmit={handleCreate}>
    <input
      type="text"
      bind:value={orgName}
      placeholder="Organization name"
      required
      disabled={creating}
    />
    <button type="submit" disabled={creating || !orgName.trim()}>
      {creating ? "Creating..." : "Create"}
    </button>
  </form>

  {#if createError}
    <div class="error-msg">{createError}</div>
  {/if}

  {#if error}
    <div class="error-msg">{error}</div>
  {/if}

  <table>
    <thead>
      <tr>
        <th>Organization ID</th>
        <th>Name</th>
        <th>Status</th>
        <th>Created</th>
        <th>Updated</th>
        <th>Action</th>
      </tr>
    </thead>
    <tbody>
      {#if organizations.length === 0 && !loading}
        <tr><td colspan="6" class="empty">No organizations</td></tr>
      {/if}
      {#each organizations as org}
        <tr>
          <td class="mono">{org["organization-id"]}</td>
          <td>{org.name}</td>
          <td>
            <span class="status-badge"
                  class:active={org.status === "active"}>
              {org.status}
            </span>
          </td>
          <td title={org["created-at"]}>{timeAgo(org["created-at"])}</td>
          <td title={org["updated-at"]}>{timeAgo(org["updated-at"])}</td>
          <td>
            {#if org["organization-id"] === selectedOrgId}
              <span class="default-badge">Default</span>
            {:else}
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

  h2 {
    margin: 0;
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

  .create-form {
    display: flex;
    gap: 0.5rem;
    margin-bottom: 1rem;
  }

  .create-form input {
    flex: 1;
    padding: 0.5rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.9rem;
    background: var(--bg-input);
    color: var(--text);
  }

  .create-form button {
    padding: 0.5rem 1rem;
    background: #16a34a;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.9rem;
    cursor: pointer;
  }

  .create-form button:disabled {
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
