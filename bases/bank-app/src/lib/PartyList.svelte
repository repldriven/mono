<script>
  import { list_parties, open_cash_account, list_cash_account_products } from "./api.mjs";
  import { time_ago } from "./time.mjs";
  import { onMount } from "svelte";

  let { onAccountOpened, headerActions, showToast } = $props();

  let parties = $state([]);
  let links = $state({});
  let loading = $state(false);
  let error = $state(null);
  let currentQuery = $state(null);
  let publishedProducts = $state([]);
  let dropdownOpen = $state({});

  function queryFromLink(url) {
    const idx = url.indexOf("?");
    return idx >= 0 ? url.substring(idx + 1) : null;
  }

  async function loadProducts() {
    try {
      const res = await list_cash_account_products();
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        const published = (res.body.versions ?? [])
          .filter(v => v.status === "published"
                    && v["account-type"] !== "internal"
                    && v["account-type"] !== "settlement");
        const latestByProduct = new Map();
        for (const v of published) {
          const pid = v["product-id"];
          const existing = latestByProduct.get(pid);
          if (!existing || v["version-number"] > existing["version-number"]) {
            latestByProduct.set(pid, v);
          }
        }
        publishedProducts = [...latestByProduct.values()];
      }
    } catch (_) { /* ignore */ }
  }

  export async function load(queryString) {
    loading = true;
    error = null;
    currentQuery = queryString ?? null;
    try {
      const [partiesRes] = await Promise.all([
        list_parties(queryString),
        loadProducts(),
      ]);
      if (partiesRes["http-status"] >= 200 && partiesRes["http-status"] < 300) {
        parties = partiesRes.body.parties ?? [];
        links = partiesRes.body.links ?? {};
      } else {
        error = partiesRes.body?.error ?? `HTTP ${partiesRes["http-status"]}`;
        parties = [];
        links = {};
      }
    } catch (err) {
      error = err.message;
      parties = [];
      links = {};
    } finally {
      loading = false;
    }
  }

  let opening = $state({});

  function toggleDropdown(partyId) {
    dropdownOpen[partyId] = !dropdownOpen[partyId];
  }

  function closeDropdowns() {
    dropdownOpen = {};
  }

  function handleWindowClick(e) {
    const open = Object.keys(dropdownOpen).some(k => dropdownOpen[k]);
    if (open && !e.target.closest(".dropdown")) {
      closeDropdowns();
    }
  }

  function errorDetail(body) {
    if (!body) return null;
    return body.message ?? body.error ?? body.detail
           ?? (typeof body === "string" ? body : JSON.stringify(body));
  }

  async function handleOpenAccount(party, product) {
    const partyId = party["party-id"];
    opening[partyId] = true;
    closeDropdowns();
    try {
      const currencies = product["allowed-currencies"] ?? [];
      const res = await open_cash_account({
        "party-id": partyId,
        "name": party["display-name"],
        "currency": currencies.length > 0 ? currencies[0] : "GBP",
        "product-id": product["product-id"],
      });
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        showToast?.({ type: "success", message: "Account opened" });
        await load(currentQuery);
        onAccountOpened?.();
      } else {
        showToast?.({ type: "warning", message: errorDetail(res.body) ?? `HTTP ${res["http-status"]}` });
      }
    } catch (err) {
      showToast?.({ type: "error", message: err.message });
    } finally {
      delete opening[partyId];
    }
  }

  onMount(() => load());
</script>

<svelte:window onclick={handleWindowClick} />

<section>
  <div class="header">
    <h2>
      <svg width="16" height="16" viewBox="0 0 16 16" fill="currentColor"><path d="M8 1a3 3 0 1 0 0 6 3 3 0 0 0 0-6zM2 13c0-3 2.5-5 6-5s6 2 6 5a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1z"/></svg>
      Parties
    </h2>
    <div class="header-actions">
      {#if headerActions}{@render headerActions()}{/if}
      <button class="refresh" onclick={() => load(currentQuery)} disabled={loading}>
        {loading ? "Loading..." : "Refresh"}
      </button>
    </div>
  </div>

  {#if error}
    <div class="error-msg">{error}</div>
  {/if}

  <table>
    <thead>
      <tr>
        <th>ID</th>
        <th>Display Name</th>
        <th>Type</th>
        <th>Status</th>
        <th>Created</th>
        <th>Updated</th>
        <th>Action</th>
      </tr>
    </thead>
    <tbody>
      {#if parties.length === 0 && !loading}
        <tr><td colspan="7" class="empty">No parties found</td></tr>
      {/if}
      {#each parties as party}
        <tr>
          <td class="mono">{party["party-id"]}</td>
          <td>{party["display-name"]}</td>
          <td>{party.type ?? ""}</td>
          <td>
            <span class="status-badge"
                  class:active={party.status === "active"}
                  class:pending={party.status === "pending"}>
              {party.status}
            </span>
          </td>
          <td title={party["created-at"]}>{time_ago(party["created-at"])}</td>
          <td title={party["updated-at"]}>{time_ago(party["updated-at"])}</td>
          <td>
            {#if party.type === "person" && party.status === "active"}
              {#if opening[party["party-id"]]}
                <button class="action-btn" disabled>Opening...</button>
              {:else if publishedProducts.length === 0}
                <span class="no-products">No products</span>
              {:else}
                <div class="dropdown">
                  <button
                    class="action-btn"
                    onclick={() => toggleDropdown(party["party-id"])}
                  >
                    + New Account &#9662;
                  </button>
                  {#if dropdownOpen[party["party-id"]]}
                    <div class="dropdown-menu">
                      {#each publishedProducts as product}
                        <button
                          class="dropdown-item"
                          onclick={() => handleOpenAccount(party, product)}
                        >
                          {product.name}
                          <span class="account-type">{product["account-type"]}</span>
                        </button>
                      {/each}
                    </div>
                  {/if}
                </div>
              {/if}
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>

  <div class="pagination">
    <button
      disabled={!links.prev || loading}
      onclick={() => load(queryFromLink(links.prev))}
    >
      Prev
    </button>
    <button
      disabled={!links.next || loading}
      onclick={() => load(queryFromLink(links.next))}
    >
      Next
    </button>
  </div>
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
    display: flex;
    align-items: center;
    gap: 0.5rem;
  }

  .header-actions {
    display: flex;
    gap: 0.5rem;
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

  .pagination {
    display: flex;
    justify-content: center;
    gap: 0.5rem;
    margin-top: 1rem;
  }

  .pagination button {
    padding: 0.4rem 1rem;
    background: var(--bg-pagination);
    border: 1px solid var(--border-input);
    border-radius: 4px;
    cursor: pointer;
    font-size: 0.85rem;
  }

  .pagination button:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  .pagination button:not(:disabled):hover {
    background: var(--bg-hover);
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

  .status-badge.pending {
    background: #fef9c3;
    color: #854d0e;
  }

  .action-btn {
    padding: 0.25rem 0.6rem;
    background: #16a34a;
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
    background: #15803d;
  }

  .no-products {
    color: var(--text-faint);
    font-size: 0.8rem;
  }

  .dropdown {
    position: relative;
    display: inline-block;
  }

  .dropdown-menu {
    position: absolute;
    right: 0;
    top: 100%;
    margin-top: 2px;
    background: var(--bg-dropdown);
    border: 1px solid var(--border-input);
    border-radius: 4px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    z-index: 10;
    min-width: 180px;
  }

  .dropdown-item {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;
    padding: 0.5rem 0.75rem;
    background: none;
    border: none;
    border-bottom: 1px solid var(--border);
    font-size: 0.85rem;
    cursor: pointer;
    text-align: left;
    gap: 0.75rem;
  }

  .dropdown-item:last-child {
    border-bottom: none;
  }

  .dropdown-item:hover {
    background: var(--bg-secondary);
  }

  .account-type {
    color: var(--text-muted);
    font-size: 0.75rem;
    text-transform: lowercase;
  }
</style>
