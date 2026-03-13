<script>
  import { list_accounts, close_account } from "./api.mjs";
  import { timeAgo } from "./time.mjs";
  import { onMount } from "svelte";

  let accounts = $state([]);
  let links = $state({});
  let loading = $state(false);
  let error = $state(null);
  let currentQuery = $state(null);

  function queryFromLink(url) {
    const idx = url.indexOf("?");
    return idx >= 0 ? url.substring(idx + 1) : null;
  }

  export async function load(queryString) {
    loading = true;
    error = null;
    currentQuery = queryString ?? null;
    try {
      const res = await list_accounts(queryString);
      if (res["http-status"] >= 200 && res["http-status"] < 300) {
        accounts = res.body.accounts ?? [];
        links = res.body.links ?? {};
      } else {
        error = res.body?.error ?? `HTTP ${res["http-status"]}`;
        accounts = [];
        links = {};
      }
    } catch (err) {
      error = err.message;
      accounts = [];
      links = {};
    } finally {
      loading = false;
    }
  }

  let closing = $state({});

  async function handleClose(accountId) {
    closing[accountId] = true;
    try {
      await close_account(accountId);
      await load(currentQuery);
    } finally {
      delete closing[accountId];
    }
  }

  function scanOf(acct) {
    const addr = (acct["payment-addresses"] ?? [])[0];
    const scan = addr?.identifier?.scan;
    if (!scan) return null;
    return `${scan["sort-code"]} ${scan["account-number"]}`;
  }

  onMount(() => load());
</script>

<section>
  <div class="header">
    <h2>Accounts</h2>
    <button class="refresh" onclick={() => load(currentQuery)} disabled={loading}>
      {loading ? "Loading..." : "Refresh"}
    </button>
  </div>

  {#if error}
    <div class="error-msg">{error}</div>
  {/if}

  <table>
    <thead>
      <tr>
        <th>Org ID</th>
        <th>Account ID</th>
        <th>Party ID</th>
        <th>SCAN</th>
        <th>Currency</th>
        <th>Status</th>
        <th>Created</th>
        <th>Updated</th>
        <th>Action</th>
      </tr>
    </thead>
    <tbody>
      {#if accounts.length === 0 && !loading}
        <tr><td colspan="9" class="empty">No accounts found</td></tr>
      {/if}
      {#each accounts as acct}
        <tr>
          <td class="mono">{acct["organization-id"]}</td>
          <td class="mono">{acct["account-id"]}</td>
          <td class="mono">{acct["party-id"]}</td>
          <td class="mono">{scanOf(acct) ?? ""}</td>
          <td>{acct.currency}</td>
          <td>
            <span class="status-badge"
                  class:opened={acct["account-status"] === "opened"}
                  class:opening={acct["account-status"] === "opening"}
                  class:closing={acct["account-status"] === "closing"}
                  class:closed={acct["account-status"] === "closed"}>
              {acct["account-status"]}
            </span>
          </td>
          <td title={acct["created-at"]}>{timeAgo(acct["created-at"])}</td>
          <td title={acct["updated-at"]}>{timeAgo(acct["updated-at"])}</td>
          <td>
            {#if acct["account-status"] === "opened"}
              <button
                class="close-btn"
                disabled={closing[acct["account-id"]]}
                onclick={() => handleClose(acct["account-id"])}
              >
                {closing[acct["account-id"]] ? "Closing..." : "Close"}
              </button>
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

  .status-badge.opened {
    background: #dcfce7;
    color: #166534;
  }

  .status-badge.opening {
    background: #fef9c3;
    color: #854d0e;
  }

  .status-badge.closing {
    background: #ffedd5;
    color: #9a3412;
  }

  .status-badge.closed {
    background: #fee2e2;
    color: #991b1b;
  }

  .close-btn {
    padding: 0.25rem 0.6rem;
    background: #dc2626;
    color: white;
    border: none;
    border-radius: 4px;
    font-size: 0.8rem;
    cursor: pointer;
  }

  .close-btn:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .close-btn:not(:disabled):hover {
    background: #b91c1c;
  }
</style>
