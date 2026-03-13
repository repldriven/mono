<script>
  import { list_account_products, create_account_product, publish_version } from "./api.mjs";
  import { timeAgo } from "./time.mjs";
  import { onMount } from "svelte";

  let versions = $state([]);
  let loading = $state(false);
  let error = $state(null);

  // create form
  let name = $state("Current Account");
  let accountType = $state("CURRENT");
  let balanceSheetSide = $state("LIABILITY");
  let allowedCurrencies = $state("GBP");
  let creating = $state(false);
  let createError = $state(null);

  let publishing = $state({});

  export async function load() {
    loading = true;
    error = null;
    try {
      const res = await list_account_products();
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        versions = res.body.versions ?? [];
      } else {
        error = res.body?.error ?? `HTTP ${res["http-status"]}`;
        versions = [];
      }
    } catch (err) {
      error = err.message;
      versions = [];
    } finally {
      loading = false;
    }
  }

  async function handleCreate(e) {
    e.preventDefault();
    creating = true;
    createError = null;
    try {
      const currencies = allowedCurrencies.split(",").map(s => s.trim()).filter(Boolean);
      const res = await create_account_product({
        "name": name,
        "account-type": accountType,
        "balance-sheet-side": balanceSheetSide,
        "allowed-currencies": currencies.length > 0 ? currencies : undefined,
      });
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        await load();
      } else {
        createError = JSON.stringify(res.body);
      }
    } catch (err) {
      createError = err.message;
    } finally {
      creating = false;
    }
  }

  async function handlePublish(version) {
    const vid = version["version-id"];
    publishing[vid] = true;
    try {
      await publish_version(version["product-id"], vid);
      await load();
    } finally {
      delete publishing[vid];
    }
  }

  onMount(() => load());
</script>

<section>
  <div class="header">
    <h2>Account Products</h2>
    <button class="refresh" onclick={() => load()} disabled={loading}>
      {loading ? "Loading..." : "Refresh"}
    </button>
  </div>

  <form class="create-form" onsubmit={handleCreate}>
    <input type="text" bind:value={name} placeholder="Product name" required disabled={creating} />
    <select bind:value={accountType} disabled={creating}>
      <option value="CURRENT">Current</option>
      <option value="SAVINGS">Savings</option>
      <option value="TERM_DEPOSIT">Term Deposit</option>
    </select>
    <select bind:value={balanceSheetSide} disabled={creating}>
      <option value="LIABILITY">Liability</option>
      <option value="ASSET">Asset</option>
    </select>
    <input type="text" bind:value={allowedCurrencies} placeholder="Currencies (e.g. GBP,EUR)"
           disabled={creating} />
    <button type="submit" disabled={creating}>
      {creating ? "Creating..." : "Create Product"}
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
        <th>Product ID</th>
        <th>Name</th>
        <th>Type</th>
        <th>Version</th>
        <th>Status</th>
        <th>Currencies</th>
        <th>Created</th>
        <th>Action</th>
      </tr>
    </thead>
    <tbody>
      {#if versions.length === 0 && !loading}
        <tr><td colspan="8" class="empty">No account products found</td></tr>
      {/if}
      {#each versions as v}
        <tr>
          <td class="mono">{v["product-id"]}</td>
          <td>{v.name}</td>
          <td>{v["account-type"]}</td>
          <td>v{v["version-number"]}</td>
          <td>
            <span class="status-badge" class:published={v.status === "published"}
                  class:draft={v.status === "draft"}>
              {v.status}
            </span>
          </td>
          <td>{(v["allowed-currencies"] ?? []).join(", ") || "Any"}</td>
          <td title={v["created-at"]}>{timeAgo(v["created-at"])}</td>
          <td>
            {#if v.status === "draft"}
              <button
                class="action-btn"
                disabled={publishing[v["version-id"]]}
                onclick={() => handlePublish(v)}
              >
                {publishing[v["version-id"]] ? "Publishing..." : "Publish"}
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
    margin-top: 2rem;
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
    flex-wrap: wrap;
  }

  .create-form input, .create-form select {
    padding: 0.5rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.9rem;
    background: var(--bg-input);
    color: var(--text);
  }

  .create-form input:first-child {
    flex: 1;
    min-width: 150px;
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

  .status-badge.published {
    background: #dcfce7;
    color: #166534;
  }

  .status-badge.draft {
    background: #fef9c3;
    color: #854d0e;
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

  .action-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .action-btn:not(:disabled):hover {
    background: #1d4ed8;
  }
</style>
