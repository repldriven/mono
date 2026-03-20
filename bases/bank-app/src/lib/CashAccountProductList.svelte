<script>
  import { list_cash_account_products, create_cash_account_product, create_cash_account_product_version, publish_version } from "./api.mjs";
  import { time_ago } from "./time.mjs";
  import { onMount } from "svelte";
  import Modal from "./Modal.svelte";

  let { showToast } = $props();

  let versions = $state([]);
  let loading = $state(false);
  let error = $state(null);

  let modalOpen = $state(false);
  let name = $state("Current Account");
  let accountType = $state("current");
  let balanceSheetSide = $state("liability");
  let allowedCurrencies = $state("GBP");
  let creating = $state(false);

  const defaultBalanceProducts = [
    { type: "default",          status: "posted",           label: "Default / Posted" },
    { type: "default",          status: "pending-incoming",  label: "Default / Pending Incoming" },
    { type: "default",          status: "pending-outgoing",  label: "Default / Pending Outgoing" },
    { type: "interest-accrued", status: "posted",           label: "Interest Accrued / Posted" },
    { type: "interest-paid",    status: "posted",           label: "Interest Paid / Posted" },
  ];

  let selectedBalanceProducts = $state(defaultBalanceProducts.map(() => true));

  let publishing = $state({});

  function isLatestVersion(v) {
    const pid = v["product-id"];
    const maxVersion = Math.max(
      ...versions.filter(x => x["product-id"] === pid).map(x => x["version-number"])
    );
    return v["version-number"] === maxVersion;
  }

  let reviseModalOpen = $state(false);
  let reviseVersion = $state(null);
  let reviseName = $state("");
  let reviseAccountType = $state("current");
  let reviseBalanceSheetSide = $state("liability");
  let reviseAllowedCurrencies = $state("");
  let reviseSelectedBalanceProducts = $state(defaultBalanceProducts.map(() => true));
  let revising = $state(false);

  function openReviseModal(v) {
    reviseVersion = v;
    reviseName = v.name;
    reviseAccountType = v["account-type"] ?? "";
    reviseBalanceSheetSide = v["balance-sheet-side"] ?? "";
    reviseAllowedCurrencies = (v["allowed-currencies"] ?? []).join(", ");
    const existing = v["balance-products"] ?? [];
    reviseSelectedBalanceProducts = defaultBalanceProducts.map(bp =>
      existing.some(e =>
        e["balance-type"] === bp.type &&
        e["balance-status"] === bp.status));
    reviseModalOpen = true;
  }

  async function handleRevise(e) {
    e.preventDefault();
    revising = true;
    try {
      const currencies = reviseAllowedCurrencies.split(",").map(s => s.trim()).filter(Boolean);
      const bps = defaultBalanceProducts
        .filter((_, i) => reviseSelectedBalanceProducts[i])
        .map(bp => ({ "balance-type": bp.type, "balance-status": bp.status }));
      const res = await create_cash_account_product_version(reviseVersion["product-id"], {
        "name": reviseName,
        "account-type": reviseAccountType,
        "balance-sheet-side": reviseBalanceSheetSide,
        "allowed-currencies": currencies.length > 0 ? currencies : undefined,
        "balance-products": bps.length > 0 ? bps : undefined,
      });
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        reviseModalOpen = false;
        showToast?.({ type: "success", message: "Version created" });
        await load();
      } else {
        showToast?.({ type: "warning", message: errorDetail(res.body) ?? `HTTP ${res["http-status"]}` });
      }
    } catch (err) {
      showToast?.({ type: "error", message: err.message });
    } finally {
      revising = false;
    }
  }

  function errorDetail(body) {
    if (!body) return null;
    return body.message ?? body.error ?? body.detail
           ?? (typeof body === "string" ? body : JSON.stringify(body));
  }

  export async function load() {
    loading = true;
    error = null;
    try {
      const res = await list_cash_account_products();
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
    try {
      const currencies = allowedCurrencies.split(",").map(s => s.trim()).filter(Boolean);
      const bps = defaultBalanceProducts
        .filter((_, i) => selectedBalanceProducts[i])
        .map(bp => ({ "balance-type": bp.type, "balance-status": bp.status }));
      const res = await create_cash_account_product({
        "name": name,
        "account-type": accountType,
        "balance-sheet-side": balanceSheetSide,
        "allowed-currencies": currencies.length > 0 ? currencies : undefined,
        "balance-products": bps.length > 0 ? bps : undefined,
      });
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        modalOpen = false;
        showToast?.({ type: "success", message: "Product created" });
        await load();
      } else {
        showToast?.({ type: "warning", message: errorDetail(res.body) ?? `HTTP ${res["http-status"]}` });
      }
    } catch (err) {
      showToast?.({ type: "error", message: err.message });
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
    <h2>
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M2 3a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V3zm0 5a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v2a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V8zm1 4a1 1 0 0 0-1 1v1a1 1 0 0 0 1 1h10a1 1 0 0 0 1-1v-1a1 1 0 0 0-1-1H3z"/></svg>
      Cash Account Products
    </h2>
    <div class="header-actions">
      <button class="new-btn" onclick={() => modalOpen = true}>+ New Product</button>
      <button class="refresh" onclick={() => load()} disabled={loading}>
        {loading ? "Loading..." : "Refresh"}
      </button>
    </div>
  </div>

  <Modal open={modalOpen} onClose={() => modalOpen = false} title="New Product">
    <form onsubmit={handleCreate}>
      <label>
        Product Name
        <input type="text" bind:value={name} placeholder="Product name" required disabled={creating} />
      </label>
      <label>
        Account Type
        <select bind:value={accountType} disabled={creating}>
          <option value="current">Current</option>
          <option value="savings">Savings</option>
          <option value="term-deposit">Term Deposit</option>
        </select>
      </label>
      <label>
        Balance Sheet Side
        <select bind:value={balanceSheetSide} disabled={creating}>
          <option value="liability">Liability</option>
          <option value="asset">Asset</option>
        </select>
      </label>
      <fieldset class="checkbox-group" disabled={creating}>
        <legend>Balances</legend>
        {#each defaultBalanceProducts as bp, i}
          <label class="checkbox-label">
            <input type="checkbox" bind:checked={selectedBalanceProducts[i]} />
            {bp.label}
          </label>
        {/each}
      </fieldset>
      <label>
        Allowed Currencies
        <input type="text" bind:value={allowedCurrencies} placeholder="e.g. GBP,EUR" disabled={creating} />
      </label>
      <button type="submit" disabled={creating}>
        {creating ? "Creating..." : "Create Product"}
      </button>
    </form>
  </Modal>

  <Modal open={reviseModalOpen} onClose={() => reviseModalOpen = false} title="Revise Product">
    <form onsubmit={handleRevise}>
      <label>
        Product Name
        <input type="text" bind:value={reviseName} placeholder="Product name" required disabled={revising} />
      </label>
      <label>
        Account Type
        <select bind:value={reviseAccountType} disabled={revising}>
          <option value="current">Current</option>
          <option value="savings">Savings</option>
          <option value="term-deposit">Term Deposit</option>
        </select>
      </label>
      <label>
        Balance Sheet Side
        <select bind:value={reviseBalanceSheetSide} disabled={revising}>
          <option value="liability">Liability</option>
          <option value="asset">Asset</option>
        </select>
      </label>
      <fieldset class="checkbox-group" disabled={revising}>
        <legend>Balances</legend>
        {#each defaultBalanceProducts as bp, i}
          <label class="checkbox-label">
            <input type="checkbox" bind:checked={reviseSelectedBalanceProducts[i]} />
            {bp.label}
          </label>
        {/each}
      </fieldset>
      <label>
        Allowed Currencies
        <input type="text" bind:value={reviseAllowedCurrencies} placeholder="e.g. GBP,EUR" disabled={revising} />
      </label>
      <button type="submit" disabled={revising}>
        {revising ? "Creating..." : "Create Version"}
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
          <td title={v["created-at"]}>{time_ago(v["created-at"])}</td>
          <td>
            {#if v["account-type"] !== "internal" && v["account-type"] !== "settlement"}
              {#if v.status === "draft"}
                <button
                  class="action-btn"
                  disabled={publishing[v["version-id"]]}
                  onclick={() => handlePublish(v)}
                >
                  {publishing[v["version-id"]] ? "Publishing..." : "Publish"}
                </button>
              {:else if v.status === "published" && isLatestVersion(v)}
                <button class="action-btn" onclick={() => openReviseModal(v)}>
                  Revise
                </button>
              {/if}
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

  form input, form select {
    padding: 0.5rem;
    border: 1px solid var(--border-input);
    border-radius: 4px;
    font-size: 0.9rem;
    background: var(--bg-input);
    color: var(--text);
  }

  .checkbox-group {
    border: 1px solid var(--border-input);
    border-radius: 4px;
    padding: 0.75rem;
    margin: 0;
  }

  .checkbox-group legend {
    font-weight: 500;
    font-size: 0.9rem;
    padding: 0 0.25rem;
  }

  .checkbox-group:disabled {
    opacity: 0.6;
  }

  .checkbox-label {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 0.5rem;
    font-weight: 400;
    font-size: 0.85rem;
    padding: 0.2rem 0;
    cursor: pointer;
  }

  .checkbox-label input[type="checkbox"] {
    width: auto;
    margin: 0;
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
